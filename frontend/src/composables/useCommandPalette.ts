import { ref } from 'vue'

const visible = ref(false)

function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    visible.value = !visible.value
  }
}

if (typeof document !== 'undefined') {
  document.addEventListener('keydown', onKeydown)
}

export function useCommandPalette() {
  function open() {
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function toggle() {
    visible.value = !visible.value
  }

  return { visible, open, close, toggle }
}
