package club.thegamerclub.nodelab;

import java.time.LocalDate;

public final class AgeCheckMathTest {
    private static void eq(Object want,Object got){
        if(!want.equals(got))throw new AssertionError("Expected "+want+" but got "+got);
    }
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] ignored){
        AgeCheckMath.Range fifteen=AgeCheckMath.range(15,LocalDate.of(2026,10,18));
        eq(LocalDate.of(2010,10,19),fifteen.first);
        eq(LocalDate.of(2011,10,18),fifteen.last);
        check(!fifteen.summary().contains("2012"),"15-year-old must not have a 2012 birthday");
        check(fifteen.details().contains("2010: Oct 19–31"),"First partial month missing");
        check(fifteen.details().contains("2011:"),"Second year missing");
        check(fifteen.details().contains("Oct 1–18"),"Last partial month missing");
        AgeCheckMath.Range zero=AgeCheckMath.range(0,LocalDate.of(2024,2,29));
        eq(LocalDate.of(2023,3,1),zero.first);
        eq(LocalDate.of(2024,2,29),zero.last);
        AgeCheckMath.Range newYear=AgeCheckMath.range(15,LocalDate.of(2026,1,1));
        eq(LocalDate.of(2010,1,2),newYear.first);
        eq(LocalDate.of(2011,1,1),newYear.last);
        AgeCheckMath.Range at120=AgeCheckMath.range(120,LocalDate.of(2026,10,18));
        eq(LocalDate.of(1905,10,19),at120.first);
        try{
            AgeCheckMath.range(121,LocalDate.now());
            throw new AssertionError("Age above supported range was accepted");
        }catch(IllegalArgumentException expected){}
        try{
            AgeCheckMath.range(-1,LocalDate.now());
            throw new AssertionError("Negative age was accepted");
        }catch(IllegalArgumentException expected){}
        System.out.println("PASS offline age ranges, birth-month boundaries and input guards");
    }
}
