
{
	arg hz, in, mod1, mod2, mod3, mod4, mod5;
	var shape = mod1.linexp(0, 1, 0.999, 0.5);
	var fc = hz * (2**mod2);
	var rq = mod3.linexp(0, 1, 2, 0.01);
	var detune = mod5 * [32, -32];
	var snd, asnd;
	fc = Lag.kr(fc, 0.2);
	snd = Saw.ar(hz + detune);
	snd = LPF.ar(snd, 12000);
	asnd = snd.abs;
	snd = snd * (asnd + shape) / (snd*snd + (shape-1) * snd + 1);
	snd = Resonz.ar(snd, fc, rq);
}