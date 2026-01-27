package io.ahxxm.ic.ui

import io.ahxxm.ic.domain.FolderSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderBrowserViewModelTest {

    private lateinit var fakeRepository: FakeFolderRepository
    private lateinit var viewModel: FolderBrowserViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        fakeRepository = FakeFolderRepository()
        viewModel = FolderBrowserViewModel(fakeRepository, testDispatcher)
    }

    @Test
    fun `loadFolders fetches from repository and updates state`() = runTest(testDispatcher) {
        viewModel.onPermissionChanged(true)
        fakeRepository.folders = listOf(folder1)

        viewModel.loadFolders()
        advanceUntilIdle()

        assertEquals(listOf(folder1), viewModel.state.value.folders)
        assertEquals(1, fakeRepository.getFoldersCallCount)
    }

    @Test
    fun `loadFolders can be called multiple times for fresh data`() = runTest(testDispatcher) {
        viewModel.onPermissionChanged(true)
        fakeRepository.folders = listOf(folder1)

        viewModel.loadFolders()
        advanceUntilIdle()
        assertEquals(listOf(folder1), viewModel.state.value.folders)

        fakeRepository.folders = listOf(folder1, folder2)

        viewModel.loadFolders()
        advanceUntilIdle()
        assertEquals(listOf(folder1, folder2), viewModel.state.value.folders)
        assertEquals(2, fakeRepository.getFoldersCallCount)
    }

    @Test
    fun `loadFolders works when list is empty and finds new folders`() = runTest(testDispatcher) {
        viewModel.onPermissionChanged(true)
        fakeRepository.folders = emptyList()

        viewModel.loadFolders()
        advanceUntilIdle()
        assertEquals(emptyList<FolderSummary>(), viewModel.state.value.folders)

        fakeRepository.folders = listOf(folder1)

        viewModel.loadFolders()
        advanceUntilIdle()
        assertEquals(listOf(folder1), viewModel.state.value.folders)
    }

    @Test
    fun `loadFolders does nothing without permission`() = runTest(testDispatcher) {
        viewModel.onPermissionChanged(false)
        fakeRepository.folders = listOf(folder1)

        viewModel.loadFolders()
        advanceUntilIdle()

        assertEquals(emptyList<FolderSummary>(), viewModel.state.value.folders)
        assertEquals(0, fakeRepository.getFoldersCallCount)
    }

    companion object {
        private val folder1 = FolderSummary(
            bucketId = 1L,
            name = "Camera",
            imageCount = 10,
            totalSizeBytes = 1_000_000L
        )
        private val folder2 = FolderSummary(
            bucketId = 2L,
            name = "Screenshots",
            imageCount = 5,
            totalSizeBytes = 500_000L
        )
    }
}

class FakeFolderRepository : FolderRepository {
    var folders: List<FolderSummary> = emptyList()
    var getFoldersCallCount: Int = 0
        private set

    override suspend fun getFolders(): List<FolderSummary> {
        getFoldersCallCount++
        return folders
    }
}
