/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vzg.kartenspeicher;


import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoordinateConverter {


    private static final String REG_EXP_STRING = "[ \\/-]*([EW])[ ]?([0-9]?[0-9]?[0-9]?[0-9][ °]?[0-9][0-9][ °]?[0-9]?[0-9])?"
            + "[ \\/-]*([EW])[ ]?([0-9]?[0-9]?[0-9][ °]?[0-9][0-9][ °]?[0-9]?[0-9])?"
            + "[ \\/-]*([NS])[ ]?([0-9]?[0-9]?[0-9][ °]?[0-9][0-9][ °]?[0-9]?[0-9])?"
            + "[ \\/-]*([NS])[ ]?([0-9]?[0-9]?[0-9][ °]?[0-9][0-9][ °]?[0-9]?[0-9])?";

    private static final Pattern PATTERN = Pattern.compile(REG_EXP_STRING);

    private static final int WESTLICHSTER = 0;
    private static final int NOERDLICHSTER = 2;
    private static final int OESTLICHSTER = 1;
    private static final int SUEDLICHSTER = 3;

    public static String convertCoordinate(String coordinate) {
        Matcher matcher = PATTERN.matcher(coordinate);

        if (!matcher.matches() || matcher.groupCount() != 8) {
            invalidInfo(coordinate);
            return null; // need 4 coordinates with 4 directions
        }
        double[] vals = new double[4];

        for (int i = 0; i < 4; i++) {
            char direction = matcher.group(i * 2 + 1).charAt(0);
            String numbersAsString = matcher.group(i * 2 + 2);
            String[] numbers = numbersAsString.split("[° ]");
            String firstNumber;
            String secondNumber;
            String thirdNumber = null;
            if( numbers.length == 3) {
                firstNumber = numbers[0];
                secondNumber = numbers[1];
                thirdNumber = numbers[2];
            } else if (numbers.length == 2) {
                firstNumber = numbers[0];
                secondNumber = numbers[1];
            } else if (numbers.length == 1) {
                if (numbers[0].length() == 5) {
                    firstNumber = numbers[0].substring(0, 3);
                    secondNumber = numbers[0].substring(3, 5);
                } else {
                    invalidInfo(coordinate);
                    return null;
                }
            } else {
                invalidInfo(coordinate);
                return null;
            }
            int deg1 = Integer.parseInt(firstNumber, 10);
            int deg2 = Integer.parseInt(secondNumber, 10);
            int deg3 = thirdNumber!=null? Integer.parseInt(thirdNumber, 10): 0;

            double result = (direction == 'E' || direction == 'N') ?
                    ((double) deg1) + (deg2 / 60.0)  + (deg3 / 3600.0):
                    -(((double) deg1) + (deg2 / 60.0) + (deg3 / 3600.0));

            vals[i] = result;
        }
        return String.format(Locale.ROOT, "%f %f, %f %f, %f %f, %f %f ",
                vals[WESTLICHSTER], vals[NOERDLICHSTER],
                vals[OESTLICHSTER], vals[NOERDLICHSTER],
                vals[OESTLICHSTER], vals[SUEDLICHSTER],
                vals[WESTLICHSTER], vals[SUEDLICHSTER]
        );
    }
    public static void invalidInfo(String coord) {
        //LOGGER.error("Coordinate is invalid: " + coord);
    }

    public static void main(String[] args) {
        System.out.println(convertCoordinate("E 005 32 00E 005 33 00N 053 11 00N 053 11 30"));
    }
}
