- multitimbral, static polyphony synth

- all voicing control to be performed externally

- voices have inputs and may be cross-modulated

## synth definitions

- synth voices defined as .scd in `lib/waves`

- each synth script returns a function:
  - function should return a ugen graph
  - with 5 arg  (hz, in, mod1, mod2, mod3, ), result is wrapped in VCA+env->pan->level
  - with 9 args (hz, in, mod1, mod2, mod3, atk, dec, sus, rel), result is wrapped in pan->level
  - with 11 args (hz, in, mod1, mod2, mod3, atk, dec, sus, rel, pan, level), result is used directly
