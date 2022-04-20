local musicutil = require 'musicutil'
local zw = include 'lib/zwaves'

engine.name = 'Zwaves'

nvoices = zw.num_voices

local handle_midi_data = function(data)
    local com = data[1] & 0xf0
    local fn = nil
    if com == 0x80 then 
        print('noteoff', data[2], data[3])
        engine.release_note(data[2])
    elseif com == 0x90 then
        print('noteon', data[2], data[3])
        engine.play_note_hz(data[2], musicutil.note_num_to_freq(data[2]))
    elseif com == 0xe0 then
        print('pitchbend', data[2], data[3])
    end
end

init = function()
    print('adding params...')

    ---- engine params
    zw.add_params()
    
    print('setting default params...')
    params:default()
    
    print('connecting midi...')
    m = midi.connect()
    m.event = handle_midi_data
    
    print('init done')
end

redraw = function()
    screen.clear()
    screen.update()
end