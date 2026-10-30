package me.rakkoon69.rakunloots.config;
import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.roll.LootChance;
import java.util.*;

/** Independent 70/100-digit positive-transition oracle, not another quadrature. */
public class ProbePositiveReference {
    public static void main(String[] ignored) {
        List<ContentEntry> entries=new ArrayList<>();
        for(int i=0;i<500;i++) entries.add(new ContentEntry("e"+i,ItemReference.of("","diamond"),1,
                i<250?1:2,OptionalInt.empty()));
        long start=System.nanoTime();
        var chances=LootChance.calculate(new Roll(250,250,entries));
        double maxError=0, sum=0;
        for(int i=0;i<500;i++) {
            var chance=Objects.requireNonNull(chances.get("e"+i));
            if(chance.kind()!=LootChance.Kind.DRAWABLE) throw new AssertionError("drawable kind");
            double expected=i<250?0.38190117577919908724:0.61809882422080091276;
            maxError=Math.max(maxError,Math.abs(chance.probability()-expected));
            sum+=chance.probability();
        }
        System.out.printf(Locale.ROOT,"light=%.17g heavy=%.17g total=%.17g maxMarginalError=%.12g ms=%.1f%n",
                chances.get("e0").probability(),chances.get("e250").probability(),sum,maxError,
                (System.nanoTime()-start)/1e6);
        if(!Double.isFinite(maxError)||maxError>1e-9) throw new AssertionError("absolute per-entry error exceeds1e-9");
    }
}
