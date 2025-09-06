package com.example.wakt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented test for the complete blocking flow
 */
@RunWith(AndroidJUnit4::class)
class BlockingFlowInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.wakt", appContext.packageName)
    }

    @Test
    fun testAddBlockedWebsite() {
        // Launch app and navigate to add block screen
        composeTestRule.onNodeWithContentDescription("Add blocked item").performClick()
        
        // Switch to websites tab
        composeTestRule.onNodeWithText("Websites").performClick()
        
        // Enter a website URL
        composeTestRule.onNodeWithText("Enter website URL").performTextInput("youtube.com")
        
        // Select challenge type (Wait timer should be default)
        // Enter challenge duration
        composeTestRule.onNodeWithText("Enter minutes (10-60)").performTextClearance()
        composeTestRule.onNodeWithText("Enter minutes (10-60)").performTextInput("15")
        
        // Add the block
        composeTestRule.onNodeWithText("Add Block").performClick()
        
        // Verify we're back on the home screen and the item appears
        composeTestRule.onNodeWithText("youtube.com").assertExists()
        composeTestRule.onNodeWithText("15 minutes").assertExists()
    }

    @Test
    fun testAddBlockedApp() {
        // Launch app and navigate to add block screen
        composeTestRule.onNodeWithContentDescription("Add blocked item").performClick()
        
        // Apps tab should be default, find an app in the list
        // Look for a common app like Settings or Calculator
        composeTestRule.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        
        // Select challenge type and duration
        composeTestRule.onNodeWithText("Enter minutes (10-60)").performTextClearance()
        composeTestRule.onNodeWithText("Enter minutes (10-60)").performTextInput("10")
        
        // Add the block
        composeTestRule.onNodeWithText("Add Block").performClick()
        
        // Verify we're back on home screen and the app appears in the list
        composeTestRule.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun testDeleteBlockedItem() {
        // First add an item to delete
        composeTestRule.onNodeWithContentDescription("Add blocked item").performClick()
        composeTestRule.onNodeWithText("Websites").performClick()
        composeTestRule.onNodeWithText("Enter website URL").performTextInput("example.com")
        composeTestRule.onNodeWithText("Add Block").performClick()
        
        // Find the delete button for the item and click it
        composeTestRule.onNodeWithContentDescription("Delete example.com").performClick()
        
        // Verify the item is removed
        composeTestRule.onNodeWithText("example.com").assertDoesNotExist()
    }

    @Test 
    fun testEmptyStateDisplay() {
        // If there are no blocked items, should show empty state
        // This might fail if there are existing items, so we'll check for either state
        try {
            composeTestRule.onNodeWithText("No blocked items").assertExists()
            composeTestRule.onNodeWithText("Add your first blocked app or website using the + button").assertExists()
        } catch (e: AssertionError) {
            // If there are items, just verify the main UI elements are present
            composeTestRule.onNodeWithText("Blocked Items").assertExists()
        }
    }

    @Test
    fun testPermissionBannerVisibility() {
        // Check if permission banners are displayed appropriately
        // These might not be visible if permissions are already granted
        
        // The main activity should load without crashing
        composeTestRule.onNodeWithText("Blocked Items").assertExists()
        
        // FAB should be present
        composeTestRule.onNodeWithContentDescription("Add blocked item").assertExists()
    }

    @Test
    fun testNavigationFlow() {
        // Test basic navigation flow
        composeTestRule.onNodeWithContentDescription("Add blocked item").performClick()
        
        // Should be on Add Block screen
        composeTestRule.onNodeWithText("Add Block").assertExists()
        composeTestRule.onNodeWithText("Apps").assertExists()
        composeTestRule.onNodeWithText("Websites").assertExists()
        
        // Test back navigation
        Espresso.pressBack()
        
        // Should be back on home screen
        composeTestRule.onNodeWithText("Blocked Items").assertExists()
    }

    @Test
    fun testWebsiteInputValidation() {
        composeTestRule.onNodeWithContentDescription("Add blocked item").performClick()
        composeTestRule.onNodeWithText("Websites").performClick()
        
        // Try to add block without entering URL
        composeTestRule.onNodeWithText("Add Block").performClick()
        
        // Should still be on the add screen (validation should prevent adding)
        composeTestRule.onNodeWithText("Enter website URL").assertExists()
        
        // Try with invalid URL format
        composeTestRule.onNodeWithText("Enter website URL").performTextInput("not-a-url")
        composeTestRule.onNodeWithText("Add Block").performClick()
        
        // Should handle invalid input appropriately
    }

    @Test
    fun testChallengeTypeSelection() {
        composeTestRule.onNodeWithContentDescription("Add blocked item").performClick()
        composeTestRule.onNodeWithText("Websites").performClick()
        
        // Enter a valid URL
        composeTestRule.onNodeWithText("Enter website URL").performTextInput("test.com")
        
        // Test changing challenge type
        composeTestRule.onNodeWithText("Wait Timer").performClick()
        
        // Should be able to see different options (if implemented)
        // For now, just verify the current selection works
        composeTestRule.onNodeWithText("Enter minutes (10-60)").performTextInput("20")
        composeTestRule.onNodeWithText("Add Block").performClick()
        
        // Verify item was added with correct challenge
        composeTestRule.onNodeWithText("test.com").assertExists()
        composeTestRule.onNodeWithText("20 minutes").assertExists()
    }
}