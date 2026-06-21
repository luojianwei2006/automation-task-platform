<template>
  <div class="page-container">
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索标题" clearable style="width:240px"
          @clear="loadData" @keyup.enter="loadData">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="showUploadDialog">上传背景音乐</el-button>
      </div>

      <!-- 音频播放器（隐藏） -->
      <audio ref="audioRef" style="display:none" @ended="onAudioEnded" />

      <!-- 卡片网格 -->
      <div v-loading="loading" class="card-grid">
        <el-empty v-if="!loading && tableData.length===0" description="暂无背景音乐" />
        <div v-for="item in tableData" :key="item.id" class="music-card"
          :class="{ playing: playingId===item.id }">
          <div class="card-icon" @click="togglePlay(item)">
            <el-icon :size="36">
              <VideoPlay v-if="playingId!==item.id" />
              <VideoPause v-else />
            </el-icon>
          </div>
          <div class="card-info">
            <div class="card-title" :title="item.title">{{ item.title }}</div>
            <div class="card-meta">
              <span class="project-tag">{{ item.projectName || '-' }}</span>
              <span>{{ formatDuration(item.duration) }}</span>
              <span>{{ formatSize(item.fileSize) }}</span>
            </div>
            <div class="card-actions">
              <el-button size="small" text type="primary" @click="handleDownload(item)">下载</el-button>
              <el-button size="small" text type="danger" @click="handleDelete(item)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="pagination">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
          :total="pagination.total" layout="total,prev,pager,next,jumper" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传背景音乐" width="500px" @close="resetUploadForm">
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入音乐标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="音频文件" prop="file">
          <el-upload ref="uploadElRef" :auto-upload="false" :limit="1"
            :on-change="handleFileChange" :on-remove="handleFileRemove" :on-exceed="handleExceed"
            accept="audio/*" drag>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em></div>
            <template #tip><div class="el-upload__tip">MP3/WAV/OGG/AAC，≤50MB</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible=false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUploadSubmit">确定上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref,reactive,computed,onMounted,onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import type { FormInstance,FormRules,UploadInstance } from 'element-plus'
import { getMaterialList,uploadMaterialFile,deleteMaterial } from '@/api/publish'
import type { Material } from '@/api/publish'

const route = useRoute()
const loading = ref(false)
const tableData = ref<Material[]>([])
const keyword = ref('')
const projectId = computed(() => Number(route.params.id)||0)
const pagination = reactive({ page:1, size:20, total:0 })

const audioRef = ref<HTMLAudioElement>()
const playingId = ref<number|null>(null)

function getFileUrl(url:string|undefined):string {
  if(!url) return ''
  if(url.startsWith('http')) return url
  return '/api'+(url.startsWith('/')?url:'/'+url)
}
function togglePlay(row:Material){
  const url = getFileUrl(row.fileUrl)
  if(!url){ ElMessage.warning('文件地址无效'); return }
  if(playingId.value===row.id){ audioRef.value?.pause(); playingId.value=null; return }
  if(audioRef.value){ audioRef.value.src=url; audioRef.value.play().catch(()=>ElMessage.error('播放失败')); playingId.value=row.id }
}
function onAudioEnded(){ playingId.value=null }

function formatDuration(s:number|undefined):string {
  if(s==null) return '-'
  return `${Math.floor(s/60)}:${Math.floor(s%60).toString().padStart(2,'0')}`
}
function formatSize(b:number|undefined):string {
  if(b==null) return '-'
  if(b<1024) return b+'B'
  if(b<1048576) return (b/1024).toFixed(1)+'KB'
  return (b/1048576).toFixed(1)+'MB'
}

async function loadData(){
  if(!projectId.value) return
  loading.value=true
  try {
    const res = await getMaterialList({ page:pagination.page, size:pagination.size, type:'music', projectId:projectId.value, keyword:keyword.value||undefined })
    tableData.value=res.records||[]; pagination.total=res.total||0
  }catch(e:any){ ElMessage.error(e.message||'加载失败') }
  finally { loading.value=false }
}

const uploading = ref(false)
const uploadVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadElRef = ref<UploadInstance>()
const selectedFile = ref<File|null>(null)
const uploadForm = reactive({ title:'' })
const uploadRules:FormRules = { title:[{ required:true, message:'请输入标题', trigger:'blur' }] }

function showUploadDialog(){ resetUploadForm(); uploadVisible.value=true }
function resetUploadForm(){ uploadForm.title=''; selectedFile.value=null; uploadFormRef.value?.clearValidate(); uploadElRef.value?.clearFiles() }
function handleFileChange(f:any){ selectedFile.value=f.raw||null }
function handleFileRemove(){ selectedFile.value=null }
function handleExceed(){ ElMessage.warning('只能上传一个音频文件') }

async function handleUploadSubmit(){
  if(!uploadFormRef.value) return
  try {
    await uploadFormRef.value.validate()
    if(!selectedFile.value){ ElMessage.warning('请选择音频文件'); return }
    uploading.value=true
    await uploadMaterialFile(selectedFile.value,'music',projectId.value,uploadForm.title)
    ElMessage.success('上传成功'); uploadVisible.value=false; loadData()
  }catch(e:any){ if(e.message&&e.message!=='cancel') ElMessage.error(e.message) }
  finally { uploading.value=false }
}

function handleDownload(row:Material){
  const url=getFileUrl(row.fileUrl)
  if(!url){ ElMessage.warning('文件地址无效'); return }
  const a=document.createElement('a'); a.href=url; a.download=row.title||'音乐'; a.target='_blank'; a.click()
}

async function handleDelete(row:Material){
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」？删除后进入回收站。`,'删除确认',{confirmButtonText:'确定',cancelButtonText:'取消',type:'warning'})
    await deleteMaterial(row.id); ElMessage.success('已删除'); loadData()
  }catch(e:any){ if(e!=='cancel'&&e!=='close'&&e.message) ElMessage.error(e.message) }
}

onBeforeUnmount(()=>{ if(audioRef.value){ audioRef.value.pause(); audioRef.value.src='' } })
onMounted(()=>{ loadData() })
</script>

<style scoped>
.page-container { padding:20px }
.toolbar { display:flex; gap:12px; margin-bottom:20px }
.card-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(240px,1fr)); gap:16px }
.music-card { display:flex; border:1px solid #ebeef5; border-radius:8px; padding:16px; transition:box-shadow .3s; background:#fff }
.music-card:hover { box-shadow:0 2px 12px rgba(0,0,0,.1) }
.music-card.playing { border-color:#409eff; background:#ecf5ff }
.card-icon { width:56px; height:56px; border-radius:50%; background:#f0f2f5; display:flex; align-items:center; justify-content:center; cursor:pointer; flex-shrink:0; margin-right:14px; color:#409eff }
.playing .card-icon { background:#409eff; color:#fff }
.card-info { flex:1; min-width:0; display:flex; flex-direction:column; gap:6px }
.card-title { font-size:14px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
.card-meta { display:flex; gap:10px; font-size:12px; color:#909399 }
.project-tag { background:#ecf5ff; color:#409eff; padding:0 6px; border-radius:3px; font-size:11px }
.card-actions { display:flex; gap:4px; margin-top:2px }
.pagination { margin-top:20px; display:flex; justify-content:flex-end }
</style>
