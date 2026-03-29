package tw.com.johnnyhng.eztalk.asr.data.classes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun defaultModelsContainExpectedSeedModel() {
        assertTrue(Model.MODELS.isNotEmpty())

        val seed = Model.MODELS.first()
        assertEquals("Multi-language", seed.name)
        assertTrue(seed.modelPath.endsWith(".onnx"))
        assertTrue(seed.tokensPath.endsWith("tokens.txt"))
    }
}
