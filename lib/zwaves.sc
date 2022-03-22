
Zwaves {

	var numVoices;
	var defs;
	var groups;
	var busses;
	var voices;
	var inputs;
	var outputs;
	var patches;

	var responders;
	var registered;
	var nodeEndCallbacks;

	*new { arg aNumVoices = 16, server;
		^super.new.init(aNumVoices);
	}

	init { arg aNumVoices, server;
		if (server.isNil, { server = Server.default; });
		numVoices = aNumVoices;
		defs = Dictionary.new;
		groups = Dictionary.with([
			\voice, Group.new(server)
		]);
		busses= Dictionary.with([
			\voice_in, Array.fill(numVoices, { Bus.audio(server, 1) }),
			\voice_out, Array.fill(numVoices, { Bus.audio(server, 2) })
		]);
		voices = Dictionary.new;
		inputs = Dictionary.new;
		outputs = Dictionary.new;
		registered = Dictionary.new;
		nodeEndCallbacks = Dictionary.new;

		responders = Dictionary.with([
			\end, OSCFunc({ arg msg;
				var id = msg[1];
				if(registered[id].notNil, {
					handleNodeEnd(id);
				});
			}, 'n_end', server.addr),
			\off, OSCFunc({ arg msg;
				var id = msg[1];
				if(registered[id].notNil, {
					handleNodeOff(id);
				});
			}, 'n_off', server.addr)
		]);

	}

	handleNodeEnd { arg id;
		var cb;
		registered[id] = nil;
		cb = nodeEndCallbacks[id];
		if (cb.notNil, {
			cb.value;
			nodeEndCallbacks[id] = nil;
		});
	}

	handleNodeOff { arg id;
		registered[id] = \paused;
	}

	addVoice { arg slot, def;
		if (voices[slot].isNil, {
			voices[slot] = Synth.newPaused(def, [
				\in, busses[\voice_in][slot].index,
				\out, busses[\voice_out][slot].index
			], groups[\voice], \addToTail);
			registered[slot] = \paused
		}, {
			// replace existing voice in slot
			switch(registered[slot],
				{\paused}, {
					voices[slot].free;
					voices[slot] = nil;
					addVoice(slot, def);
				},
				{\playing}, {
					nodeEndCallbacks[voices[slot].nodeID] = {
						voices[slot].free;
						voices[slot] = nil;
						addVoice(slot, def);
					};
					voices[slot].set(\gate, 0);
				}
			);
		});
	}

	openVoiceGate { arg slot;
		voices[slot].set(\gate, 1);
		voices[slot].run(true);
		registered[slot] = \playing;
	}

	closeVoiceGate { arg slot;
		voices[slot].set(\gate, 0);
	}


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
