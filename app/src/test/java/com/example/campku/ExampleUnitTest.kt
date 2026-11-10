package com.example.campku

import com.example.campku.util.Validators
import com.example.campku.util.estimateTotal
import com.example.campku.util.formatRupiah
import com.example.campku.util.rentalDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Uji unit logika perhitungan biaya sewa dan validasi form Android (FR-01, FR-09, FR-13). */
class ExampleUnitTest {

    @Test
    fun `FR-09 perhitungan hari dan total biaya sewa`() {
        assertEquals(1, rentalDays("2026-11-10", "2026-11-10"))
        assertEquals(3, rentalDays("2026-11-10", "2026-11-13"))
        assertNull(rentalDays("2026-11-13", "2026-11-10")) // tanggal kembali sebelum sewa

        // 3 hari x Rp 60.000 x 2 unit = Rp 360.000
        assertEquals(360_000, estimateTotal("2026-11-10", "2026-11-13", 60_000, 2))
        assertEquals("Rp 360.000", formatRupiah(360_000))
    }

    @Test
    fun `FR-01 dan FR-13 validasi form`() {
        assertNull(Validators.email("user@campku.com"))
        assertNotNull(Validators.email("bukan-email"))

        assertNull(Validators.password("123456"))
        assertNotNull(Validators.password("12345"))

        assertNull(Validators.phone("081234567890"))
        assertNotNull(Validators.phone("123"))

        assertNull(Validators.nonNegativeInt("0", "Stok"))
        assertNull(Validators.nonNegativeInt("50000", "Harga"))
        assertNotNull(Validators.nonNegativeInt("-1", "Harga"))

        assertNull(Validators.positiveInt("1", "Jumlah"))
        assertNotNull(Validators.positiveInt("0", "Jumlah"))
    }
}
