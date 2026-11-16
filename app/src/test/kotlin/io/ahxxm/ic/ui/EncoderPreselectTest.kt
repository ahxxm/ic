package io.ahxxm.ic.ui

import io.ahxxm.ic.domain.Encoder
import org.junit.Assert.assertEquals
import org.junit.Test

class EncoderPreselectTest {

    @Test
    fun `screenshot folders pick mozjpeg`() {
        assertEquals(Encoder.MOZJPEG, OptionsViewModel.defaultEncoderFor("Screenshots"))
        assertEquals(Encoder.MOZJPEG, OptionsViewModel.defaultEncoderFor("Screencast"))
        assertEquals(Encoder.MOZJPEG, OptionsViewModel.defaultEncoderFor("screenshots"))
    }

    @Test
    fun `other folders pick jpegli`() {
        assertEquals(Encoder.JPEGLI, OptionsViewModel.defaultEncoderFor("Camera"))
        assertEquals(Encoder.JPEGLI, OptionsViewModel.defaultEncoderFor("Download"))
        assertEquals(Encoder.JPEGLI, OptionsViewModel.defaultEncoderFor(""))
    }

    @Test
    fun `shot alone matches`() {
        assertEquals(Encoder.MOZJPEG, OptionsViewModel.defaultEncoderFor("shotwell"))
    }
}
