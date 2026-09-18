package com.xmarcade.ui.screens.chats

import androidx.lifecycle.ViewModel
import com.xmarcade.data.concord.ConcordManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val concord: ConcordManager
) : ViewModel() {
    val groups = concord.groups
    private val _showUnreadOnly = MutableStateFlow(false)
    val showUnreadOnly: StateFlow<Boolean> = _showUnreadOnly
    private val _isBlockView = MutableStateFlow(false)
    val isBlockView: StateFlow<Boolean> = _isBlockView

    fun toggleUnreadFilter() { _showUnreadOnly.value = !_showUnreadOnly.value }
    fun toggleView() { _isBlockView.value = !_isBlockView.value }
    fun reorder(from:Int,to:Int) = concord.reorder(from,to)
    fun enterChat(groupId:String) {}
    fun enterChannel(groupId:String, channelId:String) {}
    fun createGroup() {}
    fun discoverGroups() {}
}
