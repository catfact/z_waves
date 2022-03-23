// prototype voice handling

Routine {

	s = Server.default;

	n = 8; // num voices
	d = 'sine_fb_ring';


	z = Zwaves.new(n, s);
	z.loadAllWaves('~/dust/code/z.waves/lib/waves');

	s.sync;
	n.do({ arg i; z.setVoiceDef(i, d) });

	// voice assignment
	v = ();
	v.ready = List.fill(n, {arg i; i});
	v.used = List.new;
	v.voice = {
		var steal = false;
		var x;
		if (v.ready.size > 0, {
			postln("no steal");
			x = v.ready.pop
		}, {
			steal = true;
			postln("stealing...");
			x = v.used.removeAt(0)
		});
		v.used.add(x);
		[x, steal]
	};
	v.done = { arg x; v.ready.addFirst(x); };

	// note->voice map
	m = Array.newClear(128);
	~noteon = { arg vel, num;
		var slot;
		['noteon', vel, num].postln;
		slot = v.voice.value[0];
		m[num] = slot;
		z.playVoice(slot, num.midicps, 0.1);
	};

	~noteoff = { arg vel, num;
		var slot;
		['noteoff', vel, num].postln;
		slot = m[num];
		v.done.value(num);
		m[num] = nil;
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