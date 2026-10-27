package com.astracode.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons are built from the original SVG path data of the Stitch export so shapes match the design exactly.
 * Tint them with Icon(tint = ...). Stroke icons use a 2f stroke unless noted.
 */
object AstraIcons {

    private fun vec(name: String, viewport: Float = 24f, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).apply(build).build()

    private fun ImageVector.Builder.fill(d: String, evenOdd: Boolean = false) {
        addPath(
            pathData = addPathNodes(d),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }

    private fun ImageVector.Builder.stroke(d: String, width: Float = 2f) {
        addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private const val GEAR_OUTLINE =
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"

    // ---- Brand / navigation -------------------------------------------------------------------------------------

    /** Hexagonal cube in the drawer header badge. */
    val Cube: ImageVector by lazy {
        vec("Cube") {
            stroke("M12 2L22 8.5L22 15.5L12 22L2 15.5L2 8.5L12 2z")
            stroke("M12 22L12 15.5")
            stroke("M22 8.5L12 15.5L2 8.5")
            stroke("M2 15.5L12 8.5L22 15.5")
        }
    }

    /** Wireframe cube in the project card. */
    val ProjectCube: ImageVector by lazy {
        vec("ProjectCube") { stroke("M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4") }
    }

    val Close: ImageVector by lazy { vec("Close") { stroke("M18 6L6 18M6 6l12 12") } }
    val CloseBold: ImageVector by lazy { vec("CloseBold") { stroke("M18 6L6 18M6 6l12 12", 2.2f) } }

    val Folder: ImageVector by lazy {
        vec("Folder") { fill("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z") }
    }

    val Bolt: ImageVector by lazy { vec("Bolt") { fill("M7 2v11h3v9l7-12h-4l4-8z") } }

    val Pencil: ImageVector by lazy {
        vec("Pencil") { stroke("M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z") }
    }

    val Sparkle: ImageVector by lazy {
        vec("Sparkle") {
            fill("M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5z")
        }
    }

    val Gear: ImageVector by lazy {
        vec("Gear") {
            stroke(circle(12f, 12f, 3f))
            stroke(GEAR_OUTLINE)
        }
    }

    // ---- Git / sync / status ------------------------------------------------------------------------------------

    /** Filled circle with a right arrow cut-out (git branch / git changes glyph). */
    val GitCircle: ImageVector by lazy {
        vec("GitCircle") { fill("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14.5v-3.5H9v-2h4V7.5l4.5 4.5-4.5 4.5z") }
    }

    val Plus: ImageVector by lazy { vec("Plus") { fill("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") } }

    val Sync: ImageVector by lazy {
        vec("Sync") {
            fill("M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z")
        }
    }

    val Grid: ImageVector by lazy { vec("Grid") { fill("M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z") } }

    val Cloud: ImageVector by lazy {
        vec("Cloud") { fill("M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z") }
    }

    /** 20x20 viewport check mark. */
    val Check: ImageVector by lazy {
        vec("Check", 20f) {
            fill("M16.707 5.293a1 1 0 0 1 0 1.414l-8 8a1 1 0 0 1-1.414 0l-4-4a1 1 0 0 1 1.414-1.414L8 12.586l7.293-7.293a1 1 0 0 1 1.414 0z", evenOdd = true)
        }
    }

    /** 20x20 viewport chevron (accordion). */
    val ChevronDown: ImageVector by lazy {
        vec("ChevronDown", 20f) {
            fill("M5.293 7.293a1 1 0 0 1 1.414 0L10 10.586l3.293-3.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 0 1 0-1.414z", evenOdd = true)
        }
    }

    val Lock: ImageVector by lazy {
        vec("Lock") {
            fill("M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z")
        }
    }

    val Link: ImageVector by lazy {
        vec("Link") {
            fill("M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z")
        }
    }

    val MoreVert: ImageVector by lazy {
        vec("MoreVert") {
            fill("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z")
        }
    }

    // ---- Explorer rows ------------------------------------------------------------------------------------------

    val Clock: ImageVector by lazy { vec("Clock") { stroke("M12 8v4l3 3m6-3a9 9 0 1 1-18 0 9 9 0 0 1 18 0z") } }
    val Search: ImageVector by lazy { vec("Search") { stroke("M21 21l-6-6m2-5a7 7 0 1 1-14 0 7 7 0 0 1 14 0z") } }
    val ChevronsRight: ImageVector by lazy { vec("ChevronsRight") { stroke("M13 5l7 7-7 7M5 5l7 7-7 7") } }

    // ---- File operations ----------------------------------------------------------------------------------------

    val EditSquare: ImageVector by lazy {
        vec("EditSquare") {
            stroke("M11 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-5m-1.414-9.414a2 2 0 1 1 2.828 2.828L11.828 15H9v-2.828l8.586-8.586z")
        }
    }
    val PlusLines: ImageVector by lazy { vec("PlusLines") { stroke("M12 4v16m8-8H4") } }
    val FolderPlus: ImageVector by lazy {
        vec("FolderPlus") { stroke("M9 13h6m-3-3v6m-9 1V7a2 2 0 0 1 2-2h6l2 2h6a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    }
    val Save: ImageVector by lazy {
        vec("Save") { stroke("M8 7H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4") }
    }
    val SaveAll: ImageVector by lazy { vec("SaveAll") { stroke("M8 7v10m4-10v10m4-10v10") } }

    // ---- AI ------------------------------------------------------------------------------------------------------

    val Chat: ImageVector by lazy {
        vec("Chat") {
            stroke("M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-5l-5 5v-5z")
        }
    }
    val BoxPlus: ImageVector by lazy {
        vec("BoxPlus") {
            fill("M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-2 10h-4v4h-2v-4H7v-2h4V7h2v4h4v2z")
        }
    }

    // ---- Bottom dock ---------------------------------------------------------------------------------------------

    val Terminal: ImageVector by lazy { vec("Terminal") { stroke("M8 9l3 3-3 3m5 0h3") } }
    val Play: ImageVector by lazy { vec("Play") { stroke("M5 3L19 12L5 21L5 3z") } }

    // ---- Workspace options capsule -------------------------------------------------------------------------------

    val FileAdd: ImageVector by lazy {
        vec("FileAdd") {
            stroke("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z")
            stroke("M14 2L14 8L20 8")
            stroke("M12 18L12 12")
            stroke("M9 15L15 15")
        }
    }
    val FolderAdd: ImageVector by lazy {
        vec("FolderAdd") {
            stroke("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z")
            stroke("M12 11L12 17")
            stroke("M9 14L15 14")
        }
    }
    val Trash: ImageVector by lazy {
        vec("Trash") {
            stroke("M3 6L5 6L21 6")
            stroke("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
            stroke("M10 11L10 17")
            stroke("M14 11L14 17")
        }
    }
}