local zw = {}

zw.num_voices = 8
zw.wave_names = {
    'sine_fb_ring',
    'saw_shape_bp',
}

zw.add_params = function()
    local e = engine
    for voice=1,8 do
        params:add({id='wave_'..voice, type='option', options=zw.wave_names,
        action=function(idx)
            e.voice_wave(voice, zw.wave_names[idx])
        end})
    end
    for voice=1,8 do
        for mod=1,5 do
            local modstr = 'mod'..mod
            params:add({id=modstr..'_'..voice, type='control',
            action=function(val)
                e.voice_param(voice, modstr, val)
            end})
        end
        for mod=1,5 do
            local modstr = 'modEnv'..mod
            params:add({id=modstr..'_'..voice, type='control',
            action=function(val)
                e.voice_param(voice, modstr, val)
            end})
        end
        --[[
        params:add({id='level'})
        params:add({id='pan'})
        params:add({id='ampAtk'})
        params:add({id='ampDec'})
        params:add({id='ampSus'})
        params:add({id='ampRel'})
        params:add({id='modAtk'})
        params:add({id='modDec'})
        params:add({id='modSus'})
        params:add({id='modRel'})
        --]]
    end
end

return zw