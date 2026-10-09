package com.megawalls.rules;

import com.megawalls.game.DeathmatchZone;
import com.megawalls.game.DeathmatchZone.Wall;
import com.megawalls.menu.ClassCatalog;
import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.EnergyRules;
import java.util.List;

/** Focused JVM rules check: no game clients, worlds or user files are opened. */
public final class EnergyCenterChecks {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    public static void main(String[] args) {
        for (int cls : new int[]{3, 10}) {
            for (int phase : new int[]{0, 2, 3, 4}) {
                var d = new MWPlayerData();
                d.setClassId(cls); d.setPhase(phase); d.setPractice(phase == 0 || phase == 4); d.setInGame(true);
                check(EnergyRules.regeneration(d, phase, 20) == 0, "initial gate class/phase " + cls + "/" + phase);
                d.recordEpsHit();
                // Repeated status snapshots must not reset the hit count in normal combat/practice.
                d.setPhase(phase); d.setPractice(phase == 0 || phase == 4); d.setInGame(true);
                check(d.getEpsHits() == 1 && EnergyRules.regeneration(d, phase, 40) == 0, "one hit remains locked through sync");
                check(MWPlayerData.fromNbt(d.toNbt()).getEpsHits() == 1, "partial progress survives serialization");
                d.recordEpsHit();
                int expected = cls == 3 ? (phase == 3 ? 10 : 6) : (phase == 3 ? 8 : 5);
                check(EnergyRules.regeneration(d, phase, 60) == expected, "second hit unlocks unchanged EPS");
                check(EnergyRules.regeneration(d, phase, 61) == 0, "per-second cadence");
                d.recordEpsHit();
                check(d.getEpsHits() == 2, "extra hits cannot bank next cast progress");
                check(MWPlayerData.fromNbt(d.toNbt()).getEpsHits() == 2, "unlocked progress survives serialization");
                d.requireEpsHits();
                check(EnergyRules.regeneration(d, phase, 80) == 0, "spending skill locks gate again");
                d.recordEpsHit();
                check(EnergyRules.regeneration(d, phase, 100) == 0, "new cast needs two fresh hits");
                d.resetEpsGate();
                check(d.getEpsHits() == 0, "respawn/new round locks gate");
            }
            var d = new MWPlayerData(); d.setClassId(cls); d.setInGame(true); d.setPhase(2);
            d.recordEpsHit(); d.recordEpsHit(); d.setPhase(3);
            check(d.getEpsHits() == 2, "entering deathmatch preserves earned gate");
            d.setPhase(1);
            check(d.getEpsHits() == 0, "entering preparation resets previous round");
            d.recordEpsHit(); d.recordEpsHit(); d.setInGame(false);
            check(d.getEpsHits() == 0, "leaving active combat resets gate");
            d.recordEpsHit(); d.setClassId(cls == 3 ? 10 : 3);
            check(d.getEpsHits() == 0, "class change resets gate");
            var legacy = d.toNbt(); legacy.remove("eps_gate_version"); legacy.putInt("eps_hits", 2);
            check(MWPlayerData.fromNbt(legacy).getEpsHits() == 0, "old factory-unlocked profile migrates to locked");
            var malformed = d.toNbt(); malformed.putInt("eps_hits", 99);
            check(MWPlayerData.fromNbt(malformed).getEpsHits() == 2, "serialized count clamps to two");
            malformed.putInt("eps_hits", -5);
            check(MWPlayerData.fromNbt(malformed).getEpsHits() == 0, "serialized count clamps to zero");
            check(EnergyRules.perHit(cls, false) == 0 && EnergyRules.perHit(cls, true) == 0, "hits unlock EPS without adding direct energy");
        }
        var hunter = new MWPlayerData(); hunter.setClassId(11);
        check(EnergyRules.regeneration(hunter, 2, 20) == 2, "other class automatic energy unchanged");
        var mole = new MWPlayerData(); mole.setClassId(12);
        check(EnergyRules.regeneration(mole, 2, 20) == 0 && EnergyRules.regeneration(mole, 3, 20) == 2, "mole phase rules unchanged");

        // Actual recorded map coordinates; test central corners that the old 56-block circle rejected.
        var walls = List.of(new Wall(-5174,659,-4987,659), new Wall(-4987,659,-4987,846),
                new Wall(-4987,846,-5174,846), new Wall(-5174,659,-5174,846));
        var bounds = DeathmatchZone.enclosedBounds(walls, -5079.5, 753.5);
        check(bounds != null, "configured four walls enclose central rectangle");
        check(bounds.contains(-5173.5,659.5) && bounds.contains(-4986.5,846.5), "central corners and former wall blocks are safe");
        check(!bounds.contains(-5174.01,750) && !bounds.contains(-4986,750), "west/east outside edges rejected");
        check(!bounds.contains(-5080,658.99) && !bounds.contains(-5080,847), "north/south outside edges rejected");
        check(DeathmatchZone.contains(walls,-5079.5,753.5,56,-5173.5,659.5), "wall rectangle takes precedence over old radius");
        check(!DeathmatchZone.contains(List.of(),-5079.5,753.5,56,-5173.5,659.5), "old map fallback keeps configured radius");
        check(DeathmatchZone.contains(List.of(),0,0,56,56,0) && !DeathmatchZone.contains(List.of(),0,0,56,56.01,0), "fallback circle edge");
        check(DeathmatchZone.enclosedBounds(List.of(walls.get(0)), -5079.5,753.5) == null, "single divider is not an enclosure");
        var reversed = walls.stream().map(w -> new Wall(w.x2(),w.z2(),w.x1(),w.z1())).toList();
        check(bounds.equals(DeathmatchZone.enclosedBounds(reversed,-5079.5,753.5)), "point order does not change safe region");
        var gap = List.of(walls.get(0), walls.get(1), walls.get(2), new Wall(-5174,700,-5174,800));
        check(DeathmatchZone.enclosedBounds(gap,-5079.5,753.5) == null, "incomplete wall rectangle uses fallback");
        check(DeathmatchZone.enclosedBounds(walls,0,0) == null, "stale walls around another center rejected");
        for (var entry : ClassCatalog.ALL)
            check(!entry.tip().contains("2017") && !entry.tip().contains("待核实") && !entry.tip().contains("真实伤害先扣吸收生命"), "menu disclaimer absent: " + entry.name());
        System.out.println("ENERGY_CENTER_CHECKS_PASSED " + checks);
    }
}
