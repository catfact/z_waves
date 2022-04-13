
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
	var <modBusses;
	var <modMap;

	var <voiceSynths;
	var <voiceNodeIds;
	var <voiceNodeStatus;

	var <nodeOffResponder;

	classvar <modKeys;
	classvar <modEnvKeys;
	classvar <allParamKeys;
	classvar <numSynthParams;

	*initClass {
		modKeys = [\mod1, \mod2, \mod3, \mod4, \mod5];
		modEnvKeys = [\modEnv1, \modEnv2, \modEnv3, \modEnv4, \modEnv5];
		allParamKeys = [\hz, \level, \pan, 
		\atk, \dec, \sus, \rel,
		\modAtk, \modDec, \modSus, \modRel] ++ modKeys ++ modEnvKeys;
		numSynthParams = allParamKeys.size;
	}

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
		groups = Dictionary.new;
		groups[\voice] =  Group.tail(server);
		groups[\output] =  Group.tail(server);
		// this is counter-intuitive but correct;
		// we use InFeedback in the voice synths,
		// so all input synths should come later
		// (to avoid overwriting each other)
		groups[\input] =  Group.tail(server);

		// collection of bus arrays, indexed by role
		busses= Dictionary.newFrom([
			\voice_in, Array.fill(numVoices, { Bus.audio(server, 1) }),
			\voice_out, Array.fill(numVoices, { Bus.audio(server, 2) })
		]);

		// collection of control busses for parameter modulation
		modBusses = Dictionary.new;
		modKeys.do({ arg k; modBusses[k] = Bus.control(server, 1); });

		// flags indicating which params are mapped to mod busses for each voice
		modMap = Array.fill(numVoices, { var m = Dictionary.new;
			modKeys.do({ arg k; m[k] = false; m });
		});

		// collection of patch point synth arrays, indexed by role
		patches = Dictionary.newFrom([
			\adc_voice, Array.fill(numVoices, { arg slot;
				{ arg level=1, channel=0;
					Out.ar(busses[\voice_in][slot].index, SoundIn.ar(channel) * level.lag)
				}.play(groups[\input])
			});
			\voice_dac, Array.fill(numVoices, { arg slot;
				{ arg level=1;
					Out.ar(0, In.ar(busses[\voice_out][slot].index, 2) * level.lag)
				}.play(groups[\output])
			});
			\voice_voice, Array.fill(numVoices, { arg src;
				Array.fill(numVoices, { arg dst;
					{ arg level=0, pan=0;
						Out.ar(busses[\voice_in][dst].index,
							SelectX.ar(pan, In.ar(busses[\voice_out][src].index, 2)) * level.lag)
					}.play(groups[\output], addAction:\addToTail)
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
		busses[\voice_in].do({ arg b; b.free; });
		busses[\voice_out].do({ arg b; b.free; });

		nodeOffResponder.free;
	}

	// populate a new, empty slot with a voice synthesis node
	// optionally, provide a list of parameter values (no keys)
	createVoiceSynth { arg slot, def, params=nil;
		var synth = Synth.newPaused(def, [
			\in, busses[\voice_in][slot].index,
			\out, busses[\voice_out][slot].index
		], groups[\voice], \addToTail);
		var id = synth.nodeID;
		voiceNodeIds[slot] = id;
		voiceSynths[id] = synth;

		if (params.notNil, {
			allParamKeysdo({ arg k, i;
				synth.set(k, params[i]);
			});
		});
		synth.run(true);
	}

	// replace existing voice in slot
	replaceVoice { arg slot, def;
		var id = voiceNodeIds[slot];
		voiceSynths[id].getn(0, numSynthParams, {
			arg params;
			postln(params);
			switch(voiceNodeStatus[id],
				{\paused}, {
					voiceSynths[id].free;
				},
				{\playing}, {
					voiceSynths[id].set(\doneAction, 2);
					voiceSynths[id].set(\gate, 0);
				}
			);
			voiceSynths[id] = nil;
			voiceNodeIds[slot] = nil;
			this.createVoiceSynth(slot, def, params);
		});
	}

	openVoiceGateBySlot {arg slot;
		var id = voiceNodeIds[slot];
		this.openVoiceGateById(id);
	}

	openVoiceGateById { arg id;
		voiceSynths[id].set(\gate, 1);
		voiceSynths[id].run(true);
		voiceNodeStatus[id] = \playing;
	}

	closeVoiceGateBySlot { arg slot;
		var id =voiceNodeIds[slot];
		this.closeVoiceGateById(id);
	}

	closeVoiceGateById { arg id;
		voiceSynths[id].set(\gate, 0);
	}



	//-------------------------------------------------
	//--- public API

	// set the voice definition for a slot
	// replaces the existing synth assigned to that slot, if any
	setVoiceDef { arg slot, def;
		var id = voiceNodeIds[slot];
		if(id.isNil, {
			this.createVoiceSynth(slot, def);
		}, {
			this.replaceVoice(slot, def);
		});
	}

	// tell a given voice to start playing
	// optionally set its hz, level, and/or pan parameters
	playVoice { arg slot, params, map;
		var id;
		postln(["playVoice", params]);
		id = voiceNodeIds[slot];
		if (params.notNil, {
			voiceSynths[id].set(*params);
		});
		if(map.notNil, {
			map.keys.do({ arg key;
				setVoiceModMap(slot, key, map[key]);
			})
		});
		this.openVoiceGateById(id);
	}

	// set the modulation bus mapping for given voice, given mod key
	// value should be a Boolean
	setVoiceModMap { arg slot, key, val;
		var synth;
		if (val, {
			if (modMap[slot][key].not, {
				synth = voiceSynths[slot];
				if (synth.notNil, {
					synth.map(key, modBusses[key].index);
				});
				modMap[slot][key] = true;
			});
		}, {
			if (modMap[slot][key], {
				synth = voiceSynths[slot];
				if (synth.notNil, {
					synth.unmap(key);
				});
				modMap[slot][key] = false;
			});
		});
	}

	// set given parameter for given voice index
	// if index < 0, set for all voices
	setVoiceParam { arg slot, key, value;
		if (slot < 0, {
			voiceSynths.do({ arg synth; synth.set(key, value); });
		}, {
			var id = voiceNodeIds[slot];
			voiceSynths[id].set(key, value);
		});
	}

	setVoiceFeedbackLevel { arg src, dst, level;
		patches['voice_voice'][src][dst].set(\level, level);
	}

	setVoiceInputLevel{ arg voice, level;
		patches['adc_voice'][voice].set(\level, level);
	}

	setVoiceOutputLevel { arg voice, level;
		patches['voice_dac'][voice].set(\level, level);
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
			{ 7 }, { this.wrapWave(name, fn) },
			{ 12 }, { this.wrapWaveNoVca(name, fn) },
			{ 14 }, { this.wrapWaveNoVcaNoMix(name, fn) }
		)
	}

	wrapWave { arg name, fn;
		^SynthDef.new(name.asSymbol, {
			arg out=0, in, gate, hz, doneAction=1,
			level=0.1, pan=0,
			atk=0.1, dec=1, sus=1, rel=2,
			modAtk=0.1, modDec=1, modSus=1, modRel=2,
			mod1=0, mod2=0, mod3=0, mod4=0, mod5=0,
			modEnv1=0, modEnv2=0, modEnv3=0, modEnv4=0, modEnv5=0;
			var env, aenv, kenv, snd;
			env = Env.adsr(atk, dec, sus, rel);
			aenv = EnvGen.ar(env, gate, doneAction:doneAction);
			kenv = EnvGen.kr(Env.adsr(modAtk, modDec, modSus, modRel), gate, doneAction:0);
			mod1 = mod1.blend(kenv, modEnv1);
			mod2 = mod2.blend(kenv, modEnv2);
			mod3 = mod3.blend(kenv, modEnv3);
			mod4 = mod4.blend(kenv, modEnv4);
			mod5 = mod5.blend(kenv, modEnv5);
			snd = fn.value(hz, InFeedback.ar(in),
				mod1, mod2, mod3, mod4, mod5) * aenv;
			Out.ar(out, Lag2.kr(level) * Pan2.ar(snd, Lag2.kr(pan)));
		})
	}

	wrapWaveNoVca { arg name, fn;
		^SynthDef.new(name.asSymbol, {
			arg out=0, in, gate, hz, doneAction=1,
			level=0.1, pan=0,
			atk=0.1, dec=1, sus=1, rel=2,
			modAtk=0.1, modDec=1, modSus=1, modRel=2,
			mod1=0, mod2=0, mod3=0, mod4=0, mod5=0,
			modEnv1=0, modEnv2=0, modEnv3=0, modEnv4=0, modEnv5=0;
			var env, kenv, snd;
			env = Env.adsr(atk, dec, sus, rel);
			kenv = EnvGen.kr(Env.adsr(modAtk, modDec, modSus, modRel), gate, doneAction:0);
			mod1 = mod1.blend(kenv, modEnv1);
			mod2 = mod2.blend(kenv, modEnv2);
			mod3 = mod3.blend(kenv, modEnv3);
			mod4 = mod4.blend(kenv, modEnv4);
			mod5 = mod5.blend(kenv, modEnv5);
			snd = fn.value(hz, InFeedback.ar(in),
				mod1, mod2, mod3, mod4, mod5,
				atk, dec, sus, rel,
				gate, doneAction);
			Out.ar(out, Lag2.kr(level) * Pan2.ar(snd, Lag2.kr(pan)));
		})
	}

	wrapWaveNoVcaNoMix { arg name, fn;
		^SynthDef.new(name.asSymbol, {
			arg out=0, in, gate, hz, doneAction=1,
			level=0.1, pan=0,
			atk=0.1, dec=1, sus=1, rel=2,
			modAtk=0.1, modDec=1, modSus=1, modRel=2,
			mod1=0, mod2=0, mod3=0, mod4=0, mod5=0,
			modEnv1=0, modEnv2=0, modEnv3=0, modEnv4=0, modEnv5=0;
			var env, kenv, snd;
			env = Env.adsr(atk, dec, sus, rel);
			kenv = EnvGen.kr(Env.adsr(modAtk, modDec, modSus, modRel), gate, doneAction:0);
			mod1 = mod1.blend(kenv, modEnv1);
			mod2 = mod2.blend(kenv, modEnv2);
			mod3 = mod3.blend(kenv, modEnv3);
			mod4 = mod4.blend(kenv, modEnv4);
			mod5 = mod5.blend(kenv, modEnv5);
			snd = fn.value(hz, InFeedback.ar(in),
				mod1, mod2, mod3, mod4, mod5,
				atk, dec, sus, rel,
				gate, doneAction, level, pan);
			Out.ar(out, snd);
		})
	}
}


//--------------
//-- helper for voice slot assignment

Zwaves_MidiVoicer {
	var <numVoices;
	var <readySlots;
	var <usedSlots;
	var <numReady;
	var <numUsed;
	var <notePerSlot;
	var <slotPerNote;
	var <>stealMode;
	var <>assignMode;

	*new { arg aNumVoices; ^super.new.init(aNumVoices); }

	init { arg aNumVoices; numVoices = aNumVoices;
		readySlots = LinkedList.series(numVoices);
		usedSlots = LinkedList.new;
		numUsed = 0;
		numReady = numVoices;
		slotPerNote = Array.fill(128, {arg i; i % numVoices});
		notePerSlot = Array.series(numVoices, 48);
		stealMode = \former;
		assignMode = \former;
	}

	stealSlot {
		arg note;
		var slot = switch(stealMode,
			{\oldest}, {
				usedSlots[0]
			},
			{\newest}, {
				usedSlots[numVoices-1]
			},
			{\closest}, {
				minIndex(notePerSlot, {arg n; (note-n).abs})
			},
			{\former}, {
				slotPerNote[note]
			},
			{\random}, {
				numVoices.rand;
			}
		);
		^slot
	}

	assignSlot {
		arg note;
		var slot, idx;
		switch(assignMode,
			{\oldest}, {
				slot = readySlots.popFirst;
			},
			{\newest}, {
				slot = readySlots.pop;
			},
			{\closest}, {
				slot = minIndex(notePerSlot, {arg n; (note-n).abs});
				idx = readySlots.indexOf(slot);
				postln("closest slot: " ++ slot);
				postln("closest idx: " ++ idx);
				if(idx.isNil, {idx = 0});
				readySlots.removeAt(idx);
			},
			{\former}, {
				slot = slotPerNote[note];
				idx = readySlots.indexOf(slot);
				if (idx.isNil, {
					// the previously assigned slot isn't available;
					// fall back on "closest"
					idx = minIndex(readySlots, {arg sl; (notePerSlot[sl]-note).abs});
					slot = readySlots[idx];
				});
				postln("former slot: " ++ slot);
				postln("former idx: " ++ idx);
				if(idx.isNil, {idx = 0});
				readySlots.removeAt(idx);
			},
			{\random}, {
				idx = numReady.rand;
				postln("random idx: " ++ idx);
				if(idx.isNil, {idx = 0});
				slot = readySlots.removeAt(idx);
			}
		);
		^slot
	}

	requestSlot { arg note;
		var slot;
		if (numReady > 0, {
			postln("assigning; mode = " ++ assignMode);
			slot = this.assignSlot(note); // <- also removes from ready list
			usedSlots.add(slot);
			numReady = numReady - 1;
			numUsed = numUsed + 1;
		}, {
			postln("stealing; mode = " ++ stealMode);
			slot = this.stealSlot(note);
		});
		slotPerNote[note] = slot;
		notePerSlot[slot] = note;
		^slot
	}

	releaseSlot { arg slot;
		var idx = usedSlots.indexOf(slot);
		usedSlots.removeAt(idx);
		readySlots.add(slot);
		numUsed = numUsed - 1;
		numReady = numReady + 1;
	}

	releaseNote { arg note;
		var slot = slotPerNote[note];
		if (slot.notNil, {
			this.releaseSlot(slot);
		});
		^slot
	}
}