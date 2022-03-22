
Zwaves {
	var server;
	var numVoices;
	var defs;
	var groups;
	var busses;
	var voices;
	var inputs;
	var outputs;
	var patches;

	var voiceSynths;
	var voiceNodeIds;
	var voiceNodeStatus;

	var nodeOffResponder;

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
		groups = Dictionary.with([
			\voice, Group.new(server)
		]);

		// collection of bus arrays, indexed by role
		busses= Dictionary.with([
			\voice_in, Array.fill(numVoices, { Bus.audio(server, 1) }),
			\voice_out, Array.fill(numVoices, { Bus.audio(server, 2) })
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

	createVoiceSynth { arg slot, def, copySynth=nil;
		var synth = Synth.newPaused(def, [
			\in, busses[\voice_in][slot].index,
			\out, busses[\voice_out][slot].index
		], groups[\voice], \addToTail);
		var id = synth.nodeID;
		voiceNodeIds[slot] = id;
		voiceSynths[id] = synth;
		if (copySynth.notNil, {
			// TODO: copy existing synth params
		});
		synth.run(true);
	}

	setVoiceDef { arg slot, def;
		var id = voiceNodeIds[slot];
		if(id.isNil, {
			createVoiceSynth(slot, def);
		}, {
			// replace existing voice in slot
			switch(voiceNodeStatus[id],
				{\paused}, {
					voiceNodeIds[slot] = nil;
					createVoiceSynth(slot, def, voiceSynths[id]);
				},
				{\playing}, {
					voiceSynths[id].set(\doneAction, 2);
					voiceSynths[id].set(\gate, 0);
					voiceSynths[id] = nil;
					voiceNodeIds[slot] = nil;
					createVoiceSynth(slot, def, voiceSynths[id]);
				}
			);
		});
	}

	openVoiceGate { arg slot;
		var id =voiceNodeIds[slot];
		voiceSynths[id].set(\gate, 1);
		voiceSynths[id].run(true);
		voiceNodeStatus[id] = \playing;
	}

	closeVoiceGate { arg slot;
		var id =voiceNodeIds[slot];
		voiceSynths[id].set(\gate, 0);
	}

	//---------
	//--- manage wave definitions

	loadAllWaves { arg dir;
		dir.filesDo { arg f; loadWave(f) }
	}

	loadWave { arg path;
		var name, fn, def;
		name = PathName(path).fileNameWithoutExtension;
		fn = File.readAllString(path.asString.standardizePath).compile;
		def = wrapWaveFunction(name, fn);
		def.add(Server.default);
		defs[name.asSymbol] = def;
	}

	wrapWaveFunction { arg name, fn;
		switch(fn.numArgs,
			{ 5 }, { wrapWave(name, fn) },
			{ 9 }, { wrapWaveNoVca(name, fn) },
			{ 11 }, { wrapWaveNoVcaNoMix(name, fn) }
		)
	}

	wrapWave { arg name, fn;
		SynthDef.new(name.asSymbol, {
			arg out=0, in,
			gate, hz, level, pan,
			atk, dec, sus, rel,
			mod1, mod2, mod3;
			var aenv, snd;
			aenv = EnvGen.ar(Env.adsr(atk, dec, sus, rel), gate);
			snd = fn.value(hz, in, mod1, mod2, mod3) * aenv;
			Out.ar(out, Lag2.kr(level) * Pan2.ar(snd, Lag2.kr(pan)));
		})
	}

	wrapWaveNoVca { arg name, fn;
		SynthDef.new(name.asSymbol, {
			arg out=0, in,
			gate, hz, level, pan,
			atk, dec, sus, rel,
			mod1, mod2, mod3;
			var snd;
			snd = fn.value(hz, in, mod1, mod2, mod3, atk, dec, sus, rel, gate);
			Out.ar(out, Lag2.kr(level) * Pan2.ar(snd, Lag2.kr(pan)));
		})
	}

	wrapWaveNoVcaNoMix { arg name, fn;
		SynthDef.new(name.asSymbol, {
			arg out=0, in,
			gate, hz, level, pan,
			atk, dec, sus, rel,
			mod1, mod2, mod3;
			var snd;
			snd = fn.value(hz, in, mod1, mod2, mod3, atk, dec, sus, rel, gate, level, pan);
			Out.ar(out, snd);
		})
	}
}
