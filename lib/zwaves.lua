local zw = {}

zw.num_voices = 8
zw.wave_names = {
    'sine_fb_ring',
    'saw_shape_bp',
}

zw.add_params = function()
    local e = engine
    local str
  
    local add_voice_control = function(p, v, min, max)
      min = min and min or 0
      max = max and max or 1
      params:add({id=p..'_'..v, type='control', min=min, max=max, action=function(val)
        e.voice_param(v, p, val)
      end})
    end

    for voice=1,zw.num_voices do
        str = 'wave'
        params:add({id=str..'_'..voice, type='option', options=zw.wave_names,
        action=function(idx)
            e.voice_wave(voice, zw.wave_names[idx])
        end})
    end
    for voice=1,8 do
        for mod=1,5 do
            str = 'mod'..mod
            params:add({id=str..'_'..voice, type='control',
            action=function(val)
                e.voice_param(voice, str, val)
            end})
        end
        for mod=1,5 do
            str = 'modEnv'..mod
            params:add({id=str..'_'..voice, type='control',
            action=function(val)
                e.voice_param(voice, str, val)
            end})
        end 
        -- for _,p in ipairs({
        --   'level','pan',
        --   'ampAtk','ampDec', 'ampSus', 'ampRel',
        --   'modAtk','modDec', 'modSus', 'modRel',
        -- }) do
        --   add_voice_control(voice, p)        
        -- end
    end
end

return zw