Engine_Zwaves : CroneEngine {

    var zw;
	var zv;

	classvar numVoices = 8;


    alloc {
		zw = Zwaves.new(numVoices);
        zw.loadAllWaves("/home/we/dust/code/z_waves/lib/waves");
        zw.loadAllWaves("/home/we/dust/data/z_waves/waves");

		zv = Zwaves_MidiVoicer.new(numVoices);

		// set the wave definition for a voice slot
		// replaces the existing synth assigned to that slot, if any
		this.addCommand(\voice_wave, "is", { arg msg;
			var idx = msg[1] - 1;
			var key = msg[2].asSymbol;
			zw.setVoiceDef(idx, key);
		});

		// play a note using the voice allocator
		this.addCommand(\play_note_hz, "if", { arg msg;
			var note = msg[1];
			var hz = msg[2];
			var slot;
			postln("zwaves: play_note_hz " ++ note ++ ", "++hz);
			slot = zv.requestSlot(note);
			postln("slot: "++slot);
			zw.playVoice(slot, [\hz, hz]);
		});

        // release a note using the voice allocator
        this.addCommand(\release_note, "i", {arg msg;
            var note = msg[1];
			var hz = msg[2];
			var slot;
			postln("zwaves: release_note " ++ note);
            slot = zv.releaseNote(note);
			postln("slot: "++slot);
		    zw.closeVoiceGateBySlot(slot);
        });

		// attach or detach a mod param from the corresponding global bus,
		// for a given voice
		this.addCommand(\mod_map, "iii", { arg msg;
			var slot = msg[1] - 1;
			var modKeyIdx = msg[2] - 1;
			var val = msg[3] > 0;
			zw.setVoiceModMap(slot, Zwaves.modKeys[modKeyIdx], val);
		});

		// set given parameter for given voice index
		// if index < 0, set for all voices
		this.addCommand(\voice_param, "isf", { arg msg;
			var slot = msg[1] - 1;
			var paramKey = msg[2].asSymbol;
			var val = msg[3];
			zw.setVoiceParam(slot, paramKey, val);
		});

        // set a level in the voice feedback routing matrix
		this.addCommand(\voice_feedback_level, "iif", { arg msg;
			var src = msg[1] - 1;
			var dst = msg[2] - 1;
			var val = msg[3];
			zw.setVoiceFeedbackLevel(src, dst, val);
		});

        // set an ADC->voice patch level
		this.addCommand(\voice_input_level, "if", { arg msg;
			var slot = msg[1] - 1;
			var val = msg[2];
			zw.setVoiceInputLevel(slot, val);
		});

        // set a voice->DAC patch level
		this.addCommand(\voice_output_level, "if", { arg msg;
			var slot = msg[1] - 1;
			var val = msg[2];
			zw.setVoiceOutputLevel(slot, val);
		});

		this.addCommand(\all_notes_off, "", {
			
		})

    }


    free {
        zw.free;
    }
}
