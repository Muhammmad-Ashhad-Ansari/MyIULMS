package com.example.myiulms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentIdentityTest {

    private fun page(href: String) = """
        <div class="logininfo">You are logged in as <a href="$href"> Student Name </a>
        (<a href="https://iulms.edu.pk/login/logout.php?sesskey=abc">Logout</a>)</div>
    """.trimIndent()

    @Test
    fun `reads name and moodle id from the profile link`() {
        val identity = parseStudentIdentity(page("https://iulms.edu.pk/user/view.php?id=123&amp;course=1"))

        assertEquals("Student Name", identity.studentName)
        assertEquals("123", identity.moodleUserId)
        assertNull(identity.universityRegId)
    }

    @Test
    fun `resolves a relative profile link`() {
        assertEquals("123", parseStudentIdentity(page("/user/view.php?id=123&amp;course=1")).moodleUserId)
    }

    @Test
    fun `keeps the name when the link has no usable id`() {
        val identity = parseStudentIdentity(page("https://iulms.edu.pk/user/view.php?course=1"))

        assertEquals("Student Name", identity.studentName)
        assertNull(identity.moodleUserId)
    }

    @Test
    fun `rejects a non numeric id so it cannot reach the photo url`() {
        assertNull(parseStudentIdentity(page("/user/view.php?id=1/../2")).moodleUserId)
    }

    @Test
    fun `ignores an id on any other link`() {
        assertNull(parseStudentIdentity(page("/course/view.php?id=123")).moodleUserId)
    }

    @Test
    fun `a page without logininfo yields an empty identity`() {
        val identity = parseStudentIdentity("<html><body>No session</body></html>")

        assertEquals("", identity.studentName)
        assertNull(identity.moodleUserId)
    }

    @Test
    fun `only a non empty image body counts as a photo`() {
        val body = byteArrayOf(1)

        assertTrue(isImageResponse("image/jpeg", body))
        assertTrue(isImageResponse("IMAGE/PNG; charset=binary", body))
        assertFalse(isImageResponse("text/html; charset=utf-8", body))
        assertFalse(isImageResponse(null, body))
        assertFalse(isImageResponse("image/jpeg", ByteArray(0)))
    }
}
