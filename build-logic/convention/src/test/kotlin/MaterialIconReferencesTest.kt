import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 7 Task 8 (6b T6 M2): detekt's ForbiddenImport sees only imports, so a fully qualified Material icon reference
 * slipped past it. [materialIconReferenceLines] backs the checkNoMaterialIcons task that every module's detekt task
 * depends on.
 */
class MaterialIconReferencesTest {

    // Control: the RollaIcons line and the look-alike package name are not reported (fails if every line were).
    // Mutation: match only lines starting with "import" → the fully qualified line 3 is missed → RED.
    @Test
    fun reportsImportsAndFullyQualifiedReferencesButNotRollaIcons() {
        val fixture = listOf(
            "import androidx.compose.material.icons.filled.Add",
            "val ok = RollaIcons.Add",
            "val bad = androidx.compose.material.icons.Icons.Default.Add",
            "val alsoOk = androidx.compose.material3.Icon",
            "val spaced = androidx.compose.material\n    .icons.Icons.Default.Add",
        ).joinToString("\n")
        assertEquals(listOf(1, 3, 5), materialIconReferenceLines(fixture))
    }
}
