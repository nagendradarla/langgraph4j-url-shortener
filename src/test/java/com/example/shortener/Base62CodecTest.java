package com.example.shortener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62CodecTest {

    @Test
    void encodesPositiveValues() {
        assertEquals("1", Base62Codec.encode(1));
        assertEquals("A", Base62Codec.encode(10));
        assertTrue(Base62Codec.encode(1_000_000_000L).matches("[0-9A-Za-z]+"));
    }

    @Test
    void encodesZeroAsFirstAlphabetCharacter() {
        assertEquals("0", Base62Codec.encode(0));
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.encode(-1));
    }
}
