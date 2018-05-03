package com.pandulapeter.campfire.feature.detail.page.parsing

sealed class Note {

    companion object {
        fun toNote(input: String) = when (input[2].toLowerCase()) {
            'b' -> when (input[1].toUpperCase()) {
                'C' -> Note.B
                'D' -> Note.CSharp
                'E' -> Note.DSharp
                'F' -> Note.E
                'G' -> Note.FSharp
                'A' -> Note.GSharp
                'B' -> Note.ASharp
                else -> null
            }
            '#' -> when (input[1].toUpperCase()) {
                'C' -> Note.CSharp
                'D' -> Note.DSharp
                'E' -> Note.F
                'F' -> Note.FSharp
                'G' -> Note.GSharp
                'A' -> Note.ASharp
                'B' -> Note.C
                else -> null
            }
            else -> when (input[1].toUpperCase()) {
                'C' -> Note.C
                'D' -> Note.D
                'E' -> Note.E
                'F' -> Note.F
                'G' -> Note.G
                'A' -> Note.A
                'B' -> Note.B
                else -> Note.Hint
            }
        }
    }

    abstract fun getName(shouldUseGermanNotation: Boolean): String

    fun transpose(pitchOffset: Int): Note {
        var newNote = this
        repeat(Math.abs(pitchOffset)) {
            newNote = if (pitchOffset > 0) newNote.transposeUp() else newNote.transposeDown()
        }
        return newNote
    }

    protected abstract fun transposeUp(): Note

    protected abstract fun transposeDown(): Note

    object C : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "C"

        override fun transposeUp() = CSharp

        override fun transposeDown() = B
    }

    object CSharp : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "C#"

        override fun transposeUp() = D

        override fun transposeDown() = C
    }

    object D : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "D"

        override fun transposeUp() = DSharp

        override fun transposeDown() = CSharp
    }

    object DSharp : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "D#"

        override fun transposeUp() = E

        override fun transposeDown() = D
    }

    object E : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "E"

        override fun transposeUp() = F

        override fun transposeDown() = DSharp
    }

    object F : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "F"

        override fun transposeUp() = FSharp

        override fun transposeDown() = E
    }

    object FSharp : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "F#"

        override fun transposeUp() = G

        override fun transposeDown() = F
    }

    object G : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "G"

        override fun transposeUp() = GSharp

        override fun transposeDown() = FSharp
    }

    object GSharp : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "G#"

        override fun transposeUp() = A

        override fun transposeDown() = G
    }

    object A : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = "A"

        override fun transposeUp() = ASharp

        override fun transposeDown() = GSharp
    }

    object ASharp : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = if (shouldUseGermanNotation) "B" else "A#"

        override fun transposeUp() = B

        override fun transposeDown() = A
    }

    object B : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = if (shouldUseGermanNotation) "H" else "B"

        override fun transposeUp() = C

        override fun transposeDown() = ASharp
    }

    object Hint : Note() {
        override fun getName(shouldUseGermanNotation: Boolean) = ""

        override fun transposeUp() = Hint

        override fun transposeDown() = Hint
    }
}