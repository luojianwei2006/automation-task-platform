<template>
  <div class="amap-viewer">
    <div ref="mapContainer" class="map-container" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

const props = defineProps({
  lat: { type: Number, default: undefined },
  lng: { type: Number, default: undefined },
})

let map: any = null
let marker: any = null
const mapContainer = ref<HTMLElement>()

const GAODE_JS_KEY = import.meta.env.VITE_GAODE_API_KEY || ''

async function initMap() {
  await nextTick()
  if (!mapContainer.value) return

  if (!(window as any).AMap) {
    await loadAmapScript()
  }

  const AMap = (window as any).AMap

  if (!props.lat || !props.lng) return

  map = new AMap.Map(mapContainer.value, {
    zoom: 14,
    center: [props.lng, props.lat],
    dragEnable: false,
    keyboardEnable: false,
    doubleClickZoom: false,
    scrollWheel: false,
    touchZoom: false,
  })

  marker = new AMap.Marker({
    position: [props.lng, props.lat],
  })
  map.add(marker)
}

function loadAmapScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if ((window as any).AMap) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${GAODE_JS_KEY}`
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('高德地图加载失败'))
    document.head.appendChild(script)
  })
}

onMounted(() => {
  if (props.lat && props.lng) {
    initMap()
  }
})

// 经纬度变化时重新初始化地图
watch(() => [props.lat, props.lng], ([newLat, newLng]) => {
  if (map) {
    map.destroy()
    map = null
    marker = null
  }
  if (newLat && newLng) {
    initMap()
  }
})

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
  }
  marker = null
})

function refresh() {
  if (map) {
    map.resize()
  }
}

defineExpose({ refresh })
</script>

<style scoped>
.amap-viewer {
  width: 100%;
}
.map-container {
  width: 100%;
  height: 250px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>
