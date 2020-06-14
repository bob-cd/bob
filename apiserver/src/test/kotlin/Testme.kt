import org.junit.Assert.assertEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IntegrationTests: Spek({
    describe("Bob API Integration Tests") {
        it("should return the sum") {
            assertEquals(3, testme(2, 1))
        }
    }
})
