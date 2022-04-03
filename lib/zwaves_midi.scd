// prototype z_waves with midi

Routine {

	s = Server.default;

	n = 8; // num voices
	d = 'sine_fb_ring';


	z = Zwaves.new(n, s);
	z.loadAllWaves('~/dust/code/z.waves/lib/waves');

	s.sync;
	n.do({ arg i; z.setVoiceDef(i, d) });


	v = Zwaves_MidiVoicer.new(n);

	~defaults = [\level:0.1, \atk, 2, \rel, 1, \dec, 2.0, \sus, 0.5];

	~noteon = { arg vel, num;
		var slot, mod;
		//['noteon', vel, num].postln;
		slot = v.requestSlot(num);
		mod = vel.linlin(20, 127, 0, 1);
		z.playVoice(slot, [\hz: num.midicps, \mod1, mod] ++ ~defaults);
	};

	~noteoff = { arg vel, num;
		var slot;
		//['noteoff', vel, num].postln;
		slot = v.releaseNote(num);
		z.closeVoiceGateBySlot(slot);
	};

	MIDIIn.connectAll;
	MIDIdef.noteOn(\z_noteon, { arg vel, num;
		~noteon.value(vel, num);
	});

	MIDIdef.noteOff(\z_noteoff, {arg vel, num;
		~noteoff.value(vel, num);
	});


	n.do({ arg i;
		var pan = i.linlin(0, n-1, -1, 1);
		postln(pan);
		z.setVoiceParam(i, "pan",pan);
	});

	n.do({ arg i;
		z.setVoiceParam(i, \mod1, 1.0.rand);
		z.setVoiceParam(i, \mod2, 0.2);
		z.setVoiceParam(i, \mod3, 0.25);
		z.setVoiceParam(i, \mod4, 0.7);
		z.setVoiceParam(i, \mod5, 0.2);
	});

	n.do({ arg i;
		z.setVoiceFeedbackLevel(i, (i+3)%8, 1);
		z.setVoiceFeedbackLevel(i, (i+5)%8, 1);
	});

}.play;

/*
v.stealMode = \closest
v.assignMode = \closest

v.stealMode = \former
v.assignMode = \former

v.stealMode = \oldest
v.assignMode = \oldest
*/