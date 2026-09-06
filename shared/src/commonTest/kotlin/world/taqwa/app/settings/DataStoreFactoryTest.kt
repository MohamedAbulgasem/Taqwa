package world.taqwa.app.settings

import kotlin.test.Test
import kotlin.test.assertSame

class DataStoreFactoryTest {
    @Test
    fun everyCallerSharesOneInstanceBecauseDataStoreForbidsTwoOverOneFile() {
        assertSame(createDataStore(), createDataStore())
    }
}
