package net.wingress.mobivious.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class AppWindowLayoutTest {
    @Test fun verticalHingeUsesLargerContiguousPane() {
        assertEquals(Rect(420f,0f,1200f,800f),usableWindowPane(Rect(0f,0f,1200f,800f),Rect(400f,0f,420f,800f)))
    }
    @Test fun horizontalHingeAndResizedWindowsStayClear() {
        assertEquals(Rect(0f,420f,800f,1200f),usableWindowPane(Rect(0f,0f,800f,1200f),Rect(0f,400f,800f,420f)))
        assertEquals(Rect(0f,0f,360f,700f),usableWindowPane(Rect(0f,0f,360f,700f),Rect(400f,0f,420f,800f)))
    }
    @Test fun FlatWindowUsesWholeViewport() {
        assertEquals(Rect(0f,0f,840f,600f),usableWindowPane(Rect(0f,0f,840f,600f),null))
    }
    @Test fun keyboardCanMoveAnOccludedEditorToTheClearPane() {
        assertEquals(Rect(0f,0f,800f,400f),usableWindowPane(Rect(0f,0f,800f,1200f),Rect(0f,400f,800f,420f),500))
    }
    @Test fun ordinaryWindowsKeepTheirExistingImeInsetOwner() {
        assertEquals(Rect(0f,0f,800f,1200f),usableWindowPane(Rect(0f,0f,800f,1200f),null,500))
    }

}
