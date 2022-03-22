Engine_Zwaves : CroneEngine {

    var zw;

    alloc {
        zw = Zwaves.new;
        zw.loadAllDefs("/home/we/dust/code/z.waves/lib/waves");
        zw.loadAllDefs("/home/we/dust/data/z.waves/waves");

    }

    free {
        zw.free;
    }
}
