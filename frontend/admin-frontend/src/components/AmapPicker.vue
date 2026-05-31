<template>
  <div class="amap-picker">
    <!-- 省市区选择 -->
    <div class="region-row">
      <el-cascader
        ref="cascaderRef"
        v-model="selectedRegion"
        :options="regionOptions"
        :props="cascaderProps"
        placeholder="请选择省份/城市（可选，用于限定搜索范围）"
        clearable
        style="width: 100%"
        @change="onRegionChange"
      />
    </div>

    <!-- 地址搜索 -->
    <div class="search-row">
      <el-input
        v-model="searchKeyword"
        placeholder="输入地址关键词搜索，如：广州塔"
        clearable
        :prefix-icon="Search"
        @keyup.enter="onSearchBtn"
      >
        <template #append>
          <el-button :icon="Search" @click="onSearchBtn" />
        </template>
      </el-input>
    </div>

    <!-- 搜索结果列表 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <div
        v-for="item in searchResults"
        :key="item.id"
        class="result-item"
        @click="selectSearchResult(item)"
      >
        <div class="result-name">{{ item.name }}</div>
        <div class="result-address">{{ item.address }}</div>
      </div>
    </div>

    <!-- 地图容器：始终渲染，用 v-show 控制显隐，避免容器丢失 -->
    <div v-show="true" id="picker-map" class="map-container" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { CascaderInstance } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Object as () => { lat?: number, lng?: number },
    default: () => ({})
  },
  locationDesc: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'update:locationDesc'])

const searchKeyword = ref('')
const searchResults = ref<any[]>([])
const selectedLat = ref<number | undefined>(props.modelValue?.lat)
const selectedLng = ref<number | undefined>(props.modelValue?.lng)
const selectedRegion = ref<string[]>([])
const selectedCity = ref<string>('')
const cascaderRef = ref<CascaderInstance>()
const pendingCity = ref<string>('')  // 地图未就绪时缓存待跳转的城市

// 省市区联动配置
const cascaderProps = {
  checkStrictly: true,
  value: 'value',
  label: 'label',
  children: 'children',
}

// 中国省市数据
const regionOptions = [
  {
    value: 'beijing', label: '北京市', children: [{ value: 'beijing', label: '北京市' }]
  },
  {
    value: 'tianjin', label: '天津市', children: [{ value: 'tianjin', label: '天津市' }]
  },
  {
    value: 'shanghai', label: '上海市', children: [{ value: 'shanghai', label: '上海市' }]
  },
  {
    value: 'chongqing', label: '重庆市', children: [{ value: 'chongqing', label: '重庆市' }]
  },
  {
    value: 'hebei', label: '河北省', children: [
      { value: 'shijiazhuang', label: '石家庄' },
      { value: 'tangshan', label: '唐山' },
      { value: 'baoding', label: '保定' },
    ]
  },
  {
    value: 'shanxi', label: '山西省', children: [
      { value: 'taiyuan', label: '太原' },
      { value: 'datong', label: '大同' },
    ]
  },
  {
    value: 'liaoning', label: '辽宁省', children: [
      { value: 'shenyang', label: '沈阳' },
      { value: 'dalian', label: '大连' },
    ]
  },
  {
    value: 'jilin', label: '吉林省', children: [
      { value: 'changchun', label: '长春' },
    ]
  },
  {
    value: 'heilongjiang', label: '黑龙江省', children: [
      { value: 'haerbin', label: '哈尔滨' },
    ]
  },
  {
    value: 'jiangsu', label: '江苏省', children: [
      { value: 'nanjing', label: '南京' },
      { value: 'suzhou', label: '苏州' },
      { value: 'wuxi', label: '无锡' },
    ]
  },
  {
    value: 'zhejiang', label: '浙江省', children: [
      { value: 'hangzhou', label: '杭州' },
      { value: 'ningbo', label: '宁波' },
      { value: 'wenzhou', label: '温州' },
    ]
  },
  {
    value: 'anhui', label: '安徽省', children: [
      { value: 'hefei', label: '合肥' },
    ]
  },
  {
    value: 'fujian', label: '福建省', children: [
      { value: 'fuzhou', label: '福州' },
      { value: 'xiamen', label: '厦门' },
    ]
  },
  {
    value: 'jiangxi', label: '江西省', children: [
      { value: 'nanchang', label: '南昌' },
    ]
  },
  {
    value: 'shandong', label: '山东省', children: [
      { value: 'jinan', label: '济南' },
      { value: 'qingdao', label: '青岛' },
    ]
  },
  {
    value: 'henan', label: '河南省', children: [
      { value: 'zhengzhou', label: '郑州' },
      { value: 'luoyang', label: '洛阳' },
    ]
  },
  {
    value: 'hubei', label: '湖北省', children: [
      { value: 'wuhan', label: '武汉' },
    ]
  },
  {
    value: 'hunan', label: '湖南省', children: [
      { value: 'changsha', label: '长沙' },
    ]
  },
  {
    value: 'guangdong', label: '广东省', children: [
      { value: 'guangzhou', label: '广州' },
      { value: 'shenzhen', label: '深圳' },
      { value: 'dongguan', label: '东莞' },
      { value: 'foshan', label: '佛山' },
    ]
  },
  {
    value: 'guangxi', label: '广西壮族自治区', children: [
      { value: 'nanning', label: '南宁' },
    ]
  },
  {
    value: 'hainan', label: '海南省', children: [
      { value: 'haikou', label: '海口' },
    ]
  },
  {
    value: 'sichuan', label: '四川省', children: [
      { value: 'chengdu', label: '成都' },
    ]
  },
  {
    value: 'guizhou', label: '贵州省', children: [
      { value: 'guiyang', label: '贵阳' },
    ]
  },
  {
    value: 'yunnan', label: '云南省', children: [
      { value: 'kunming', label: '昆明' },
    ]
  },
  {
    value: 'xizang', label: '西藏自治区', children: [
      { value: 'lasa', label: '拉萨' },
    ]
  },
  {
    value: 'shaanxi', label: '陕西省', children: [
      { value: 'xian', label: '西安' },
    ]
  },
  {
    value: 'gansu', label: '甘肃省', children: [
      { value: 'lanzhou', label: '兰州' },
    ]
  },
  {
    value: 'qinghai', label: '青海省', children: [
      { value: 'xining', label: '西宁' },
    ]
  },
  {
    value: 'neimenggu', label: '内蒙古自治区', children: [
      { value: 'huhehaote', label: '呼和浩特' },
    ]
  },
  {
    value: 'ningxia', label: '宁夏回族自治区', children: [
      { value: 'yinchuan', label: '银川' },
    ]
  },
  {
    value: 'xinjiang', label: '新疆维吾尔自治区', children: [
      { value: 'wulumuqi', label: '乌鲁木齐' },
    ]
  },
  {
    value: 'xianggang', label: '香港特别行政区', children: [
      { value: 'xianggang', label: '香港' },
    ]
  },
  {
    value: 'aomen', label: '澳门特别行政区', children: [
      { value: 'aomen', label: '澳门' },
    ]
  },
]

// cascader value → 高德城市名
const cityNameMap: Record<string, string> = {
  beijing: '北京',
  tianjin: '天津',
  shanghai: '上海',
  chongqing: '重庆',
  shijiazhuang: '石家庄',
  tangshan: '唐山',
  baoding: '保定',
  taiyuan: '太原',
  datong: '大同',
  shenyang: '沈阳',
  dalian: '大连',
  changchun: '长春',
  haerbin: '哈尔滨',
  nanjing: '南京',
  suzhou: '苏州',
  wuxi: '无锡',
  hangzhou: '杭州',
  ningbo: '宁波',
  wenzhou: '温州',
  hefei: '合肥',
  fuzhou: '福州',
  xiamen: '厦门',
  nanchang: '南昌',
  jinan: '济南',
  qingdao: '青岛',
  zhengzhou: '郑州',
  luoyang: '洛阳',
  wuhan: '武汉',
  changsha: '长沙',
  guangzhou: '广州',
  shenzhen: '深圳',
  dongguan: '东莞',
  foshan: '佛山',
  nanning: '南宁',
  haikou: '海口',
  chengdu: '成都',
  guiyang: '贵阳',
  kunming: '昆明',
  lasa: '拉萨',
  xian: '西安',
  lanzhou: '兰州',
  xining: '西宁',
  huhehaote: '呼和浩特',
  yinchuan: '银川',
  wulumuqi: '乌鲁木齐',
  xianggang: '香港',
  aomen: '澳门',
}

// 城市名 → 中心坐标（硬编码，完全绕过 AMap 插件回调不触发的问题）
const cityCenterMap: Record<string, [number, number]> = {
  '北京': [116.4074, 39.9042],
  '天津': [117.1900, 39.1256],
  '上海': [121.4737, 31.2304],
  '重庆': [106.5516, 29.5630],
  '石家庄': [114.5149, 38.0428],
  '唐山': [118.1759, 39.6353],
  '保定': [115.4829, 38.8900],
  '太原': [112.5489, 37.8706],
  '大同': [113.2952, 40.0768],
  '沈阳': [123.4315, 41.8057],
  '大连': [121.6147, 38.9140],
  '长春': [125.3235, 43.8171],
  '哈尔滨': [126.5354, 45.8025],
  '南京': [118.7969, 32.0603],
  '苏州': [120.5853, 31.2989],
  '无锡': [120.3017, 31.5747],
  '杭州': [120.1614, 30.2936],
  '宁波': [121.5440, 29.8683],
  '温州': [120.6721, 28.0000],
  '合肥': [117.2272, 31.8206],
  '福州': [119.3063, 26.0745],
  '厦门': [118.1689, 24.4798],
  '南昌': [115.8921, 28.6765],
  '济南': [117.0009, 36.6758],
  '青岛': [120.3826, 36.0671],
  '郑州': [113.6401, 34.7447],
  '洛阳': [112.4345, 34.6630],
  '武汉': [114.3054, 30.5931],
  '长沙': [112.9388, 28.2282],
  '广州': [113.2644, 23.1291],
  '深圳': [114.0579, 22.5431],
  '东莞': [113.7633, 23.0430],
  '佛山': [113.1227, 23.0288],
  '南宁': [108.3665, 22.8170],
  '海口': [110.3312, 20.0319],
  '成都': [104.0665, 30.5723],
  '贵阳': [106.7135, 26.5783],
  '昆明': [102.8329, 24.8801],
  '拉萨': [91.1322, 29.6604],
  '西安': [108.9402, 34.3416],
  '兰州': [103.8236, 36.0581],
  '西宁': [101.7782, 36.6171],
  '呼和浩特': [111.7519, 40.8414],
  '银川': [106.2309, 38.4872],
  '乌鲁木齐': [87.6168, 43.8256],
  '香港': [114.1694, 22.3193],
  '澳门': [113.5491, 22.1987],
}

let map: any = null
let marker: any = null
let searchTimer: any = null

const GAODE_JS_KEY = import.meta.env.VITE_GAODE_API_KEY || ''
const GAODE_REST_KEY = import.meta.env.VITE_GAODE_REST_KEY || ''

// ============ 地图初始化（带 nextTick 保护）============
async function initMap() {
  // 如果已有地图实例，先销毁（防止重复初始化）
  if (map) {
    map.destroy()
    map = null
  }
  // 等 DOM 更新完成后再初始化
  await nextTick()

  const container = document.getElementById('picker-map')
  if (!container) {
    console.error('[AmapPicker] #picker-map 容器不存在，无法初始化地图')
    return
  }

  if (!(window as any).AMap) {
    await loadAmapScript()
  }

  const AMap = (window as any).AMap

  map = new AMap.Map('picker-map', {
    zoom: 14,
    center: props.modelValue?.lat && props.modelValue?.lng
      ? [props.modelValue.lng, props.modelValue.lat]
      : [116.39747, 39.908823],
  })

  map.on('click', (e: any) => {
    const lat = e.lnglat.getLat()
    const lng = e.lnglat.getLng()
    selectedLat.value = lat
    selectedLng.value = lng
    updateMarker(lat, lng)
    reverseGeocode(lat, lng)
  })

  if (props.modelValue?.lat && props.modelValue?.lng) {
    updateMarker(props.modelValue.lat, props.modelValue.lng)
  }

  // 如果选择城市时地图尚未初始化，现在补跳
  if (pendingCity.value) {
    panToCity(pendingCity.value)
    pendingCity.value = ''
  }
}

// ============ 城市选择变化 ============
function onRegionChange(val: string[]) {
  // checkStrictly: true 时 blur() 无法关闭下拉，必须用 togglePopperVisible(false)
  nextTick(() => {
    cascaderRef.value?.togglePopperVisible(false)
  })

  if (val && val.length >= 2) {
    selectedCity.value = cityNameMap[val[1]] || ''
  } else if (val && val.length === 1) {
    const provinceName = getProvinceName(val[0])
    selectedCity.value = provinceName
  } else {
    selectedCity.value = ''
  }

  // 选择城市后，地图跳转到该城市
  if (selectedCity.value) {
    if (map) {
      panToCity(selectedCity.value)
    } else {
      // 地图尚未初始化完成，缓存城市名，等 initMap 完成后再跳转
      pendingCity.value = selectedCity.value
    }
  }
}

function getProvinceName(val: string): string {
  const find = regionOptions.find(r => r.value === val)
  return find
    ? find.label.replace('省', '').replace('市', '').replace('自治区', '').replace('特别行政区', '')
    : ''
}

// 城市名 → 地图跳转（硬编码坐标，完全绕过 AMap 异步插件回调不触发的问题）
function panToCity(cityName: string) {
  if (!map) return
  const coord = cityCenterMap[cityName]
  if (coord) {
    map.setCenter(coord)
    map.setZoom(12)
  }
}

// ============ 生命周期 ============
onMounted(() => {
  initMap()
})

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
  }
  marker = null
  if (searchTimer) clearTimeout(searchTimer)
})

watch(() => props.modelValue, (newVal) => {
  if (newVal?.lat && newVal?.lng && map) {
    updateMarker(newVal.lat, newVal.lng)
    selectedLat.value = newVal.lat
    selectedLng.value = newVal.lng
  }
}, { deep: true })

// 监听搜索关键词，输入 >= 2 字后防抖搜索
watch(searchKeyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  const keyword = val?.trim()
  if (!keyword || keyword.length < 2) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(() => {
    doSearch(keyword)
  }, 500)
})

// ============ Marker 更新 ============
function updateMarker(lat: number, lng: number) {
  const AMap = (window as any).AMap

  if (marker) {
    marker.setPosition([lng, lat])
  } else {
    marker = new AMap.Marker({
      position: [lng, lat],
      draggable: true,
    })
    map.add(marker)

    marker.on('dragend', (e: any) => {
      const pos = e.lnglat
      const lat = pos.getLat()
      const lng = pos.getLng()
      selectedLat.value = lat
      selectedLng.value = lng
      reverseGeocode(lat, lng)
    })
  }

  map.setCenter([lng, lat])
  emit('update:modelValue', { lat: selectedLat.value, lng: selectedLng.value })
}

// ============ 逆地理编码（使用 REST API，绕过 JS 插件回调不触发的问题）============
async function reverseGeocode(lat: number, lng: number) {
  try {
    const url = `https://restapi.amap.com/v3/geocode/regeo?location=${lng},${lat}&output=json&key=${GAODE_REST_KEY}`
    console.log('[AmapPicker] reverseGeocode URL:', url)
    const res = await fetch(url)
    const result = await res.json()
    console.log('[AmapPicker] reverseGeocode result:', result)
    if (result.status === '1') {
      const address = result.regeocode?.formatted_address || ''
      emit('update:locationDesc', address)
    } else {
      console.warn('[AmapPicker] reverseGeocode 失败:', result)
    }
  } catch (e) {
    console.error('[AmapPicker] reverseGeocode 异常:', e)
    // 逆地理编码失败时静默处理，不影响主流程
  }
}

// ============ 地址搜索（使用 REST API，绕过 JS 插件回调不触发的问题）============
async function doSearch(keyword: string) {
  if (!keyword) return

  let url = `https://restapi.amap.com/v3/place/text?keywords=${encodeURIComponent(keyword)}&offset=5&page=1&output=json&key=${GAODE_REST_KEY}&extensions=all`
  console.log('[AmapPicker] doSearch GAODE_REST_KEY:', GAODE_REST_KEY ? '已配置' : '未配置')

  // 限定搜索城市
  if (selectedCity.value) {
    url += `&city=${encodeURIComponent(selectedCity.value)}&citylimit=true`
  }
  console.log('[AmapPicker] doSearch URL:', url)

  try {
    const res = await fetch(url)
    const result = await res.json()
    console.log('[AmapPicker] doSearch result:', result)
    if (result.status === '1') {
      const pois = result.pois || []
      searchResults.value = pois.map((poi: any) => {
        const [lng, lat] = poi.location.split(',').map(Number)
        return {
          id: poi.id,
          name: poi.name,
          address: poi.address,
          location: { lng, lat },
        }
      })
      // 自动选中第一个结果并跳转地图
      if (searchResults.value.length > 0) {
        const first = searchResults.value[0]
        selectedLat.value = first.location.lat
        selectedLng.value = first.location.lng
        updateMarker(first.location.lat, first.location.lng)
        searchKeyword.value = first.name
        emit('update:locationDesc', first.address || first.name)
      }
    } else {
      console.warn('[AmapPicker] doSearch 失败:', result)
      searchResults.value = []
      ElMessage.warning('未找到相关地址，请尝试更具体的描述')
    }
  } catch (e) {
    console.error('[AmapPicker] doSearch 异常:', e)
    searchResults.value = []
    ElMessage.error('搜索请求失败，请稍后重试')
  }
}

function onSearchBtn() {
  const keyword = searchKeyword.value?.trim()
  if (!keyword || keyword.length < 2) {
    ElMessage.warning('请输入至少2个字符的地址关键词')
    return
  }
  doSearch(keyword)
}

// ============ 选择搜索结果 ============
function selectSearchResult(item: any) {
  const lng = item.location.lng
  const lat = item.location.lat
  selectedLat.value = lat
  selectedLng.value = lng
  updateMarker(lat, lng)
  searchResults.value = []
  searchKeyword.value = item.name
  emit('update:locationDesc', item.address || item.name)
}

// ============ 加载高德 JS API ============
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

defineExpose({
  getCoord: () => ({ lat: selectedLat.value, lng: selectedLng.value })
})
</script>

<style scoped>
.amap-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.region-row,
.search-row {
  width: 100%;
}

.search-results {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.result-item {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
}

.result-item:last-child {
  border-bottom: none;
}

.result-item:hover {
  background-color: #f5f7fa;
}

.result-name {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.result-address {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.map-container {
  width: 100%;
  height: 350px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>
