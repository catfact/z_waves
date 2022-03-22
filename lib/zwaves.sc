
Zwaves {
	var <server;
	var <numVoices;
	var <defs;
	var <groups;
	var <busses;
	var <voices;
	var <inputs;
	var <outputs;
	var <patches;

	var <voiceSynths;
	var <voiceNodeIds;
	var <voiceNodeStatus;

	var <nodeOffResponder;

	*new { arg aNumVoices = 16, aServer;
		^super.new.init(aNumVoices, aServer);
	}

	init { arg aNumVoices, aServer;
		server = aServer;
		if (server.isNil, { server = Server.default; });
		numVoices = aNumVoices;

		// collection of synthdefs, indexed by name
		defs = Dictionary.new;

		// collection of groups, indexed by role
		groups = Dictionary.newFrom([
			\input, Group.tail(server),
			\voice, Group.tail(server),
			\output, Group.tail(server)
		]);

		// collection of bus arrays, indexed by role
		busses= Dictionary.newFrom([
			\voice_in, Array.fill(numVoices, { Bus.audio(server, 1) }),
			\voice_out, Array.fill(numVoices, { Bus.audio(server, 2) })
		]);

		// collection of patch point synth arrays, indexed by role
		patches = Dictionary.newFrom([
			\adc_voice, Array.fill(numVoices, { arg slot;
				{ arg amp=1;
					Out.ar(busses[\voice_in][slot].index, SoundIn.ar(0) * amp.lag)
				}.play(groups[\input])
			});
			\voice_dac, Array.fill(numVoices, { arg slot;
				{ arg amp=1;
					Out.ar(0, In.ar(busses[\voice_out][slot].index, 2) * amp.lag)
				}.play(groups[\output])
			});
			\voice_voice, Array.fill(numVoices, { arg src;
				Array.fill(numVoices, { arg dst;
					{ arg amp=0;
						Out.ar(busses[\voice_in][dst].index,
							InFeedback.ar(busses[\voice_out][src].index, 2) * amp.lag)
					}.play(groups[\input])
				});
			});
		]);

		// collection of Synths, indexed by node ID
		voiceSynths = Dictionary.new;

		// array of node IDs per voice slot
		voiceNodeIds = Array.newClear(numVoices);

		// collection of status keys, indexed by node ID
		voiceNodeStatus = Dictionary.new;

		nodeOffResponder = OSCFunc({ arg msg;
			var id = msg[1];
			if(voiceNodeStatus[id].notNil, {
				voiceNodeStatus[id] = \paused;
			});
		}, 'n_off', server.addr);

	}

	free {
		// NB: don't need to free individual synths;
		// freeing the groups takes care of it
		groups[\input].free;
		groups[\voice].free;
		groups[\output].free;

		patches[\voice_voice].do({ arg arr; arr.do ({ arg p; p.free; }) });
		busses[\voice_input].do({ arg b; b.free; });
		busses[\voice_output].do({ arg b; b.free; });
	}

	// populate a new, empty slot with a voice synthesis node
	createVoiceSynth { arg slot, def, copySynth=nil;
		Routine {
			var synth = Synth.newPaused(def, [
				\in, busses[\voice_in][slot].index,
				\out, busses[\voice_out][slot].index
			], groups[\voice], \addToTail);
			var id = synth.nodeID;
			voiceNodeIds[slot] = id;
			voiceSynths[id] = synth;

			if (copySynth.notNil, {
				[\hz, \mod1, \mod2, \mod3].do({ arg k;
					copySynth.get(k, { arg val;
						synth.set(k, val);
					});
				});
				server.sync;
			});
			synth.run(true);
		}.play;
	}

	// set the voice definition for a slot
	// replaces the existing synth assigned to that slot, if any
	setVoiceDef { arg slot, def;
		var id = voiceNodeIds[slot];
		if(id.isNil, {
			this.createVoiceSynth(slot, def);
		}, {
			// replace existing voice in slot
			switch(voiceNodeStatus[id],
				{\paused}, {
					voiceNodeIds[slot] = nil;
					this.createVoiceSynth(slot, def, voiceSynths[id]);
				},
				{\playing}, {
					voiceSynths[id].set(\doneAction, 2);
					voiceSynths[id].set(\gate, 0);
					voiceSynths[id] = nil;
					voiceNodeIds[slot] = nil;
					this.createVoiceSynth(slot, def, voiceSynths[id]);
				}
			);
		});
	}

	// openVoiceGateBySlot {arg slot;
	// 	var id = voiceNodeIds[slot];
	// 	openVoiceGateById(id);
	// }

	openVoiceGateById { arg id;
		voiceSynths[id].set(\gate, 1);
		voiceSynths[id].run(true);
		voiceNodeStatus[id] = \playing;
	}

/*	closeVoiceGateBySlot { arg slot;
		var id =voiceNodeIds[slot];
		closeVoiceGateById(id);
	}*/

	closeVoiceGateById { arg id;
		voiceSynths[id].set(\gate, 0);
	}

	//-------------------------------------------------
	//--- additional convenience setters

	playVoice { arg slot, hz, level=1.0;
		var id = voiceNodeIds[slot];
		voiceSynths[id].set(\hz, hz, \level, level);
		openVoiceGateById(id);
	}

	setVoiceParam { arg slot, key, value;
		var id = voiceNodeIds[slot];
		voiceSynths[id].set(key, value);
	}

	//-------------------------------------------------
	//--- wave definition management

	loadAllWaves { arg dir;
		PathName.new(dir.asString.standardizePath).files.do{ arg f;
			this.loadWave(f)
		}
	}

	loadWave { arg path;
		var name, fn, def;
		postln("loadwave: "++path);
		name = path.fileNameWithoutExtension;
		fn = File.readAllString(path.fullPath).compile.value;
		fn.postln;
		def = this.wrapWaveFunction(name, fn);
		postln(def);
		def.send(Server.default);
		defs[name.asSymbol] = def;
	}

	wrapWaveFunction { arg name, fn;
		var nargs = fn.numArgs;
		postln("num args: " ++ nargs);
		^switch(nargs,
			{ 5 }, { this.wrapWave(name, fn) },
			{ 10 }, { this.wrapWaveNoVca(name, fn) },
			{ 12 }, { this.wrapWaveNoVcaNoMix(name, fn) }
		)
	}

	wrapWave { arg name, fn;
		^SynthDef.new(name.asSymbol, {
			arg out=0, in, gate, hz, doneAction=1,
			level=0.1, pan=0,
			atk=0.1, dec=1, sus=1, rel=2,
			mod1=0, mod2=0, mod3=0;
			var aenv, snd;
			aenv = EnvGen.ar(Env.adsr(atk, dec, sus, rel), gate, doneAction:doneAction);
			snd = fn.value(hz, in, mod1, mod2, mod3) * aenv;
			Out.ar(out, Lag2.kr(level) * Pan2.ar(snd, Lag2.kr(pan)));
		})
	}

	wrapWaveNoVca { arg name, fn;
		^SynthDef.new(name.asSymbol, {
			arg out=0, in, gate, hz, doneAction=1,
			level=0.1, pan=0,
			atk=0.1, dec=1, sus=1, rel=2,
			mod1=0, mod2=0, mod3=0;
			var snd;
			snd = fn.value(hz, in, mod1, mod2, mod3, atk, dec, sus, rel, gate, doneAction);
			Out.ar(out, Lag2.kr(level) * Pan2.ar(snd, Lag2.kr(pan)));
		})
	}

	wrapWaveNoVcaNoMix { arg name, fn;
		^SynthDef.new(name.asSymbol, {
			arg out=0, in, gate, hz, doneAction=1,
			level=0.1, pan=0,
			atk=0.1, dec=1, sus=1, rel=2,
			mod1=0, mod2=0, mod3=0;
			var snd;
			snd = fn.value(hz, in, mod1, mod2, mod3, atk, dec, sus, rel, gate, doneAction, level, pan);
			Out.ar(out, snd);
		})
	}
}
