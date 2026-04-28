const state = {
  unreadCount: 0,
  hasNewMessage: false,
  workbenchVisible: false
}

const mutations = {
  INCREMENT_UNREAD: (state) => {
    state.unreadCount++
    state.hasNewMessage = true
  },
  CLEAR_UNREAD: (state) => {
    state.unreadCount = 0
    state.hasNewMessage = false
  },
  SET_UNREAD: (state, count) => {
    state.unreadCount = count
    state.hasNewMessage = count > 0
  },
  RESET_NEW_FLAG: (state) => {
    state.hasNewMessage = false
  },
  SET_WORKBENCH_VISIBLE: (state, visible) => {
    state.workbenchVisible = visible
  }
}

const actions = {
  incrementUnread({ commit }) {
    commit('INCREMENT_UNREAD')
  },
  clearUnread({ commit }) {
    commit('CLEAR_UNREAD')
  },
  setUnread({ commit }, count) {
    commit('SET_UNREAD', count)
  },
  resetNewFlag({ commit }) {
    commit('RESET_NEW_FLAG')
  },
  setWorkbenchVisible({ commit }, visible) {
    commit('SET_WORKBENCH_VISIBLE', visible)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
