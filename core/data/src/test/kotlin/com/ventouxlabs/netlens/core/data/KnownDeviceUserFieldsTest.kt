package com.ventouxlabs.netlens.core.data

import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.FieldMerge
import com.ventouxlabs.netlens.core.data.model.FieldMergeOutcome.DROPPED
import com.ventouxlabs.netlens.core.data.model.FieldMergeOutcome.FILLED
import com.ventouxlabs.netlens.core.data.model.FieldMergeOutcome.KEPT
import com.ventouxlabs.netlens.core.data.model.FieldMergeOutcome.UNIONED
import com.ventouxlabs.netlens.core.data.model.UserDetailField
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.UserFieldLimits
import com.ventouxlabs.netlens.core.data.model.mergeUserFields
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KnownDeviceUserFieldsTest {

    private fun field(merge: List<FieldMerge>, field: UserDetailField) = merge.single { it.field == field }

    @Test
    fun `tags union keeps survivor order first and its casing on case-insensitive duplicates`() {
        val merge = mergeUserFields(UserDetails(tags = "Printer,office"), UserDetails(tags = "printer,Lab,OFFICE"))

        assertEquals("Printer,office,Lab", merge.merged.tags)
        assertEquals(FieldMerge(UserDetailField.TAGS, UNIONED, null), field(merge.fields, UserDetailField.TAGS))
    }

    @Test
    fun `tags the survivor already has are KEPT and its raw column is untouched`() {
        val merge = mergeUserFields(UserDetails(tags = "a, B"), UserDetails(tags = "b,A"))

        assertEquals("a, B", merge.merged.tags)
        assertEquals(KEPT, field(merge.fields, UserDetailField.TAGS).outcome)
    }

    @Test
    fun `loser tags past the cap are reported dropped with their values`() {
        val mine = (1..10).map { "s$it" }
        val merge = mergeUserFields(UserDetails(tags = DeviceTags.format(mine)), UserDetails(tags = "x,y,z,s1"))

        assertEquals(DeviceTags.format(mine + listOf("x", "y")), merge.merged.tags)
        assertEquals(FieldMerge(UserDetailField.TAGS, UNIONED, "z"), field(merge.fields, UserDetailField.TAGS))
    }

    @Test
    fun `a full survivor drops every new loser tag and keeps its column`() {
        val full = DeviceTags.format((1..DeviceTags.MAX_TAGS).map { "s$it" })
        val merge = mergeUserFields(UserDetails(tags = full), UserDetails(tags = "x,y"))

        assertEquals(full, merge.merged.tags)
        assertEquals(FieldMerge(UserDetailField.TAGS, DROPPED, "x, y"), field(merge.fields, UserDetailField.TAGS))
    }

    @Test
    fun `tags fill an empty survivor`() {
        val merge = mergeUserFields(UserDetails(), UserDetails(tags = "nas"))

        assertEquals("nas", merge.merged.tags)
        assertEquals(FILLED, field(merge.fields, UserDetailField.TAGS).outcome)
    }

    @Test
    fun `different notes are joined by a blank line`() {
        val merge = mergeUserFields(UserDetails(notes = " mine "), UserDetails(notes = "theirs\n"))

        assertEquals("mine\n\ntheirs", merge.merged.notes)
        assertEquals(UNIONED, field(merge.fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `notes the survivor already contains are KEPT, so a repeat or chained merge changes nothing`() {
        val first = mergeUserFields(UserDetails(notes = "mine"), UserDetails(notes = "theirs")).merged
        val again = mergeUserFields(first, UserDetails(notes = "theirs"))
        val equal = mergeUserFields(UserDetails(notes = "same"), UserDetails(notes = " same "))

        assertEquals(first, again.merged)
        assertEquals(KEPT, field(again.fields, UserDetailField.NOTES).outcome)
        assertEquals("same", equal.merged.notes)
        assertEquals(KEPT, field(equal.fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `a loser note that is only a substring of the survivor's is still combined, not silently kept`() {
        val merge = mergeUserFields(UserDetails(notes = "Living room TV"), UserDetails(notes = "TV"))

        assertEquals("Living room TV\n\nTV", merge.merged.notes)
        assertEquals(UNIONED, field(merge.fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `a loser that contains the survivor's paragraph adds only its new paragraphs`() {
        val merge = mergeUserFields(UserDetails(notes = "A"), UserDetails(notes = "a \n\n  B\n\n\nA"))

        assertEquals("A\n\nB", merge.merged.notes)
        assertEquals(UNIONED, field(merge.fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `a loser whose paragraphs are all present is KEPT and the survivor's raw text is untouched`() {
        val merge = mergeUserFields(UserDetails(notes = " A\n\nB "), UserDetails(notes = "b\n\na"))

        assertEquals(" A\n\nB ", merge.merged.notes)
        assertEquals(KEPT, field(merge.fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `a chain A into B into C keeps each paragraph once and a repeat merge changes nothing`() {
        val b = mergeUserFields(UserDetails(notes = "b"), UserDetails(notes = "a")).merged
        val c = mergeUserFields(UserDetails(notes = "c\n\na"), b).merged

        assertEquals("c\n\na\n\nb", c.notes)
        assertEquals(c, mergeUserFields(c, b).merged)
        assertEquals(KEPT, field(mergeUserFields(c, UserDetails(notes = "A")).fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `appending a paragraph leaves the survivor's own text byte-identical`() {
        val mine = "A\n\n\n\nB\n\nb"
        val merge = mergeUserFields(UserDetails(notes = "  $mine \n"), UserDetails(notes = "C"))

        assertEquals("$mine\n\nC", merge.merged.notes)
        assertEquals(UNIONED, field(merge.fields, UserDetailField.NOTES).outcome)
    }

    @Test
    fun `scalar values differing only in case or surrounding spaces are KEPT, not reported lost`() {
        val merge = mergeUserFields(UserDetails(location = "Kitchen"), UserDetails(location = " kitchen "))

        assertEquals("Kitchen", merge.merged.location)
        assertEquals(FieldMerge(UserDetailField.LOCATION, KEPT, null), field(merge.fields, UserDetailField.LOCATION))
    }

    @Test
    fun `a union exactly at the notes cap is stored, one character over drops the loser's notes untruncated`() {
        val mine = "m".repeat(100)
        val fits = "t".repeat(UserFieldLimits.MAX_NOTES_LENGTH - mine.length - 2)
        val over = fits + "t"

        val atCap = mergeUserFields(UserDetails(notes = mine), UserDetails(notes = fits))
        val overCap = mergeUserFields(UserDetails(notes = mine), UserDetails(notes = over))

        assertEquals(UserFieldLimits.MAX_NOTES_LENGTH, atCap.merged.notes?.length)
        assertEquals(UNIONED, field(atCap.fields, UserDetailField.NOTES).outcome)
        assertEquals(mine, overCap.merged.notes)
        assertEquals(FieldMerge(UserDetailField.NOTES, DROPPED, over), field(overCap.fields, UserDetailField.NOTES))
    }

    @Test
    fun `scalar fields fill when empty, otherwise the survivor wins and the loser's value is reported`() {
        val merge = mergeUserFields(
            UserDetails(customName = "Mine", location = "  ", osOverride = "Linux"),
            UserDetails(customName = "Theirs", location = "Garage", osOverride = "Linux", modelOverride = "M1"),
        )

        assertEquals(UserDetails(customName = "Mine", location = "Garage", osOverride = "Linux", modelOverride = "M1"), merge.merged)
        assertEquals(
            listOf(
                FieldMerge(UserDetailField.CUSTOM_NAME, DROPPED, "Theirs"),
                FieldMerge(UserDetailField.LOCATION, FILLED, null),
                FieldMerge(UserDetailField.OS_OVERRIDE, KEPT, null),
                FieldMerge(UserDetailField.MODEL_OVERRIDE, FILLED, null),
            ),
            merge.fields,
        )
    }

    @Test
    fun `fields empty on both sides are not listed and keep the survivor's raw value`() {
        val merge = mergeUserFields(UserDetails(notes = "  "), UserDetails(tags = ","))

        assertEquals(emptyList<FieldMerge>(), merge.fields)
        assertEquals("  ", merge.merged.notes)
        assertNull(merge.merged.tags)
    }
}
