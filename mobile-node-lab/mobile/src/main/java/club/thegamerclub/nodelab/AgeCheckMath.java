package club.thegamerclub.nodelab;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Purely local consistency calculator. No identifiers, uploads, preferences,
 * audit events or retained DOBs. A claimed age is not proof of age.
 */
public final class AgeCheckMath {
    private static final DateTimeFormatter DATE=
        DateTimeFormatter.ofPattern("MMM d, uuuu",Locale.US);
    private static final DateTimeFormatter MONTH=
        DateTimeFormatter.ofPattern("MMM",Locale.US);
    private AgeCheckMath(){}

    public static final class Range {
        public final LocalDate first, last, asOf;
        public final int age;
        Range(int age,LocalDate first,LocalDate last,LocalDate asOf){
            this.age=age;this.first=first;this.last=last;this.asOf=asOf;
        }
        public String details(){
            StringBuilder text=new StringBuilder();
            int currentYear=0;
            YearMonth current=YearMonth.from(first);
            YearMonth end=YearMonth.from(last);
            while(!current.isAfter(end)){
                if(current.getYear()!=currentYear){
                    if(currentYear!=0)text.append("\n");
                    currentYear=current.getYear();
                    text.append(currentYear).append(": ");
                }else text.append(" · ");
                LocalDate lower=current.atDay(1).isBefore(first)?first:current.atDay(1);
                LocalDate upper=current.atEndOfMonth().isAfter(last)?last:current.atEndOfMonth();
                text.append(current.format(MONTH));
                if(lower.getDayOfMonth()!=1 || upper.getDayOfMonth()!=current.lengthOfMonth()){
                    text.append(" ").append(lower.getDayOfMonth()).append("–")
                        .append(upper.getDayOfMonth());
                }
                current=current.plusMonths(1);
            }
            return text.toString();
        }
        public String summary(){
            return "Claimed age: "+age+" (as of "+asOf.format(DATE)+")"+
                "\nPossible birthdays: "+first.format(DATE)+" through "+last.format(DATE)+
                "\n\nPossible birth months (partial months show allowed days):"+
                "\n"+details()+
                "\n\nThis checks whether a claimed age and birthdate are consistent. "+
                "It does not prove anyone's age. Birthdays on Feb 29 may "+
                "require additional clarification.";
        }
    }

    public static Range range(int claimedAge,LocalDate reference){
        if(reference==null)throw new IllegalArgumentException("Missing reference date");
        if(claimedAge<0 || claimedAge>120)
            throw new IllegalArgumentException("Enter an age from 0 to 120.");
        LocalDate last=reference.minusYears(claimedAge);
        LocalDate first=reference.minusYears(claimedAge+1L).plusDays(1);
        return new Range(claimedAge,first,last,reference);
    }
}
