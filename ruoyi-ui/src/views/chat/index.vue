<template>
  <div class="app-container">
    <ChatBox />
  </div>
</template>

<script>
import ChatBox from '@/components/Chat/ChatBox';

export default {
  name: 'Chat',
  components: {
    ChatBox
  }
};
</script>

<style scoped>
.app-container {
  padding: 20px;
}
</style>
