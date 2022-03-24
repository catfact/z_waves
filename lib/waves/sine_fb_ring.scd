
{
	arg hz, input, mod1, mod2, mod3, mod4, mod5;
	var sine_fb = mod1;
	var ring_mix = mod2;
	var ring_lpf_fc = hz * (2**(mod3.linlin(0, 1, -2, 2)));
	var ring_lpf_rq = mod4.linexp(0, 1, 2, 0.001);
	var waver = mod5.linlin(0, 1, 0, 1/12);
	var snd;
	snd = SinOscFB.ar(hz * (2**(LFNoise2.ar((hz/16).max(10))*waver)), sine_fb) * AmpCompA.kr(hz);
	snd = SelectX.ar(ring_mix, [snd, (snd * RLPF.ar((input*0.666).tanh*1.5, ring_lpf_fc, ring_lpf_rq))]);
	snd
}
