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

	~noteon = { arg vel, num;
		var slot;
		//['noteon', vel, num].postln;
		slot = v.requestSlot(num);
		z.playVoice(slot, num.midicps, 0.1);
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

}.play;

/*

v.stealMode = \closest
v.assignMode = \closest

v.stealMode = \former
v.assignMode = \former

v.stealMode = \oldest
v.assignMode = \oldest
*/