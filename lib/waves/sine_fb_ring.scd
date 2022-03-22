
{
	arg hz, in, mod1, mod2, mod3;
	var sine_fb = mod1;
	var ring_mix = mod2;
	var ring_lpf_fc = mod3.linexp(0, 1, 10, 12000);
	var snd;
	snd = SinOscFB.ar(hz, sine_fb) * AmpCompA.kr(hz);
	snd = SelectX.ar(ring_mix, [snd, snd * LPF.ar(In.ar(in), ring_lpf_fc)]);
	snd

}