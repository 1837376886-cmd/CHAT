<template>
  <div :class="classObj" class="app-wrapper" :style="{'--current-color': theme}">
    <div v-if="device==='mobile'&&sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!sidebar.hide" class="sidebar-container"/>
    <div :class="{hasTagsView:needTagsView,sidebarHide:sidebar.hide}" class="main-container">
      <div :class="{'fixed-header':fixedHeader}">
        <navbar @setLayout="setLayout"/>
        <tags-view v-if="needTagsView"/>
      </div>
      <app-main/>
      <settings ref="settingRef"/>
    </div>
  </div>
</template>

<script>
import { AppMain, Navbar, Settings, Sidebar, TagsView } from './components'
import ResizeMixin from './mixin/ResizeHandler'
import { mapState } from 'vuex'
import variables from '@/assets/styles/variables.scss'
import { WS_URL, MessageType } from '@/utils/chatConstants'
import { getMyCsStatus } from '@/api/cs'

export default {
  name: 'Layout',
  components: {
    AppMain,
    Navbar,
    Settings,
    Sidebar,
    TagsView
  },
  mixins: [ResizeMixin],
  data() {
    return {
      csWsClient: null
    }
  },
  computed: {
    ...mapState({
      theme: state => state.settings.theme,
      sideTheme: state => state.settings.sideTheme,
      sidebar: state => state.app.sidebar,
      device: state => state.app.device,
      needTagsView: state => state.settings.tagsView,
      fixedHeader: state => state.settings.fixedHeader,
      csWorkbenchVisible: state => state.csNotice.workbenchVisible
    }),
    classObj() {
      return {
        hideSidebar: !this.sidebar.opened,
        openSidebar: this.sidebar.opened,
        withoutAnimation: this.sidebar.withoutAnimation,
        mobile: this.device === 'mobile'
      }
    },
    variables() {
      return variables
    },
    isCustomerService() {
      return this.$store.state.user.isCustomerService === 1
    }
  },
  mounted() {
    if (this.isCustomerService && !this.csWorkbenchVisible) {
      this.checkAndInitCsWs()
    }
  },
  beforeDestroy() {
    if (this.csWsClient) {
      this.csWsClient.close()
      this.csWsClient = null
    }
  },
  watch: {
    isCustomerService(val) {
      if (val && !this.csWsClient && !this.csWorkbenchVisible) {
        this.checkAndInitCsWs()
      } else if (!val && this.csWsClient) {
        this.csWsClient.close()
        this.csWsClient = null
      }
    },
    csWorkbenchVisible(val) {
      if (!this.isCustomerService) return
      if (val) {
        if (this.csWsClient) {
          this.csWsClient.close()
          this.csWsClient = null
        }
      } else {
        this.checkAndInitCsWs()
      }
    }
  },
  methods: {
    handleClickOutside() {
      this.$store.dispatch('app/closeSideBar', { withoutAnimation: false })
    },
    setLayout() {
      this.$refs.settingRef.openSetting()
    },
    checkAndInitCsWs() {
      if (!this.isCustomerService || this.csWorkbenchVisible) return
      getMyCsStatus().then(res => {
        const online = !!(res.data && res.data.online)
        if (online && !this.csWsClient) {
          this.initCsWebSocket()
        } else if (!online && this.csWsClient) {
          this.csWsClient.close()
          this.csWsClient = null
        }
      }).catch(() => {
        // 查询失败不做操作，避免误关
      })
    },
    initCsWebSocket() {
      const ChatWebSocket = this.$chatWebSocket
      const userInfo = this.$store.state.user
      const userId = Number(userInfo.id)

      this.csWsClient = new ChatWebSocket(WS_URL, {
        maxReconnectAttempts: 5,
        reconnectInterval: 3000,
        heartbeatInterval: 20000,
        onOpen: () => {
          this.csWsClient.send({
            type: MessageType.AUTH,
            fromUserId: userId,
            fromUserNickname: userInfo.nickName || userInfo.name,
            fromUserAvatar: userInfo.avatar || ''
          })
        },
        onMessage: (msg) => {
          this.handleCsWsMessage(msg)
        }
      })
    },
    handleCsWsMessage(msg) {
      if (msg.type === MessageType.AUTH_SUCCESS) {
        console.log('layout 客服WS认证成功')
        return
      }
      if (msg.type === MessageType.CS_CHAT) {
        // 只有当客服不在工作台页面时才触发全局通知
        // 在工作台页面时由工作台组件自己处理未读和音效
        if (!this.csWorkbenchVisible) {
          this.$store.dispatch('csNotice/incrementUnread')
          this.playNotifySound()
        }
        return
      }
      if (msg.type === MessageType.SYSTEM_NOTICE) {
        if (!this.csWorkbenchVisible) {
          this.$store.dispatch('csNotice/incrementUnread')
          this.playNotifySound()
        }
        return
      }
    },
    playNotifySound() {
      try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext
        if (!AudioCtx) return
        const ctx = new AudioCtx()
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.type = 'sine'
        osc.frequency.setValueAtTime(880, ctx.currentTime)
        osc.frequency.exponentialRampToValueAtTime(440, ctx.currentTime + 0.15)
        gain.gain.setValueAtTime(0.3, ctx.currentTime)
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.15)
        osc.start(ctx.currentTime)
        osc.stop(ctx.currentTime + 0.15)
      } catch (e) {
        console.warn('播放提示音失败', e)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
  @import "~@/assets/styles/mixin.scss";
  @import "~@/assets/styles/variables.scss";

  .app-wrapper {
    @include clearfix;
    position: relative;
    height: 100%;
    width: 100%;

    &.mobile.openSidebar {
      position: fixed;
      top: 0;
    }
  }

  .drawer-bg {
    background: #000;
    opacity: 0.3;
    width: 100%;
    top: 0;
    height: 100%;
    position: absolute;
    z-index: 999;
  }

  .fixed-header {
    position: fixed;
    top: 0;
    right: 0;
    z-index: 9;
    width: calc(100% - #{$base-sidebar-width});
    transition: width 0.28s;
  }

  .hideSidebar .fixed-header {
    width: calc(100% - 54px);
  }

  .sidebarHide .fixed-header {
    width: 100%;
  }

  .mobile .fixed-header {
    width: 100%;
  }
</style>
