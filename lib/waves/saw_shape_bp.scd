
{
	arg hz, in, mod1, mod2, mod3, mod4, mod5;
	var shape = mod1.linexp(0, 1, 0.999, 0.001);
	var fc = hz * (2**mod2);
	var rq = mod3.linexp(0, 1, 2, 0.01);
	var snd, asnd;
	fc = Lag.kr(fc, 0.2);
	//tuneMod = 2 ** (mod3.linlin(0, 2) * in);
	//snd = Saw.ar(hz * tuneMod); // + (detuneHz * [1, -1.0]));
	//snd = Mix.new(snd);
	snd = Saw.ar(hz);
	snd = LPF.ar(snd, 16000);
	asnd = snd.abs;
	snd = snd * (asnd + shape) / (snd*snd + (shape-1) * snd + 1);
	snd = Resonz.ar(snd, fc, rq);
}