package com.thulirkumver.bookstore.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BogoCalculatorTest {
    @Test void supportsEveryBuyGetQuantity() {
        assertEquals(1, BogoCalculator.freeUnits(1, 1, 1));
        assertEquals(2, BogoCalculator.freeUnits(1, 1, 2));
        assertEquals(1, BogoCalculator.freeUnits(2, 2, 1));
        assertEquals(2, BogoCalculator.freeUnits(4, 2, 1));
        assertEquals(4, BogoCalculator.freeUnits(4, 2, 2));
        assertEquals(6, BogoCalculator.freeUnits(9, 3, 2));
    }

    @Test void incompleteQualifyingGroupsDoNotReceiveGifts() {
        assertEquals(0, BogoCalculator.freeUnits(1, 2, 1));
        assertEquals(1, BogoCalculator.freeUnits(3, 2, 1));
        assertEquals(2, BogoCalculator.freeUnits(5, 2, 1));
        assertEquals(0, BogoCalculator.freeUnits(0, 1, 3));
    }

    @Test void rejectsInvalidQuantitiesAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> BogoCalculator.freeUnits(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> BogoCalculator.freeUnits(2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> BogoCalculator.freeUnits(2, 1, 0));
        assertThrows(ArithmeticException.class, () -> BogoCalculator.freeUnits(Integer.MAX_VALUE, 1, 50));
    }
}
