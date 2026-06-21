<template>
  <div class="page-container">
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索标题" clearable style="width:240px"
          @clear="loadData" @keyup.enter="loadData">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="showUploadDialog">上传视频素材</el-button>
      </div>

      <!-- 卡片网格 -->
      <div v-loading="loading" class="card-grid">
        <el-empty v-if="!loading && tableData.length===0" description="暂无视频素材" />
        <div v-for="item in tableData" :key="item.id" class="video-card">
          <div class="card-preview" @click="showPreview(item)">
            <el-icon :size="40"><VideoCamera /></el-icon>
            <div class="play-overlay"><el-icon :size="28"><VideoPlay /></el-icon></div>
          </div>
          <div class="card-info">
            <div class="card-header">
              <el-tag size="small" type="warning">#{{ item.sortOrder || '-' }}</el-tag>
              <span class="card-title" :title="item.title">{{ item.title }}</span>
            </div>
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

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewTitle" width="720px" @close="closePreview">
      <div class="video-preview-box">
        <video v-if="previewUrl" ref="videoRef" :src="previewUrl" controls autoplay
          style="width:100%;max-height:480px">您的浏览器不支持 video 标签</video>
        <el-empty v-else description="无法加载视频" />
      </div>
    </el-dialog>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传视频素材" width="520px" @close="resetUploadForm">
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="80px">
        <el-form-item label="段落序号" prop="paragraphOrder">
          <el-input-number v-model="uploadForm.paragraphOrder" :min="1" style="width:100%" />
          <div class="form-tip">视频在最终合成中的排列顺序</div>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入视频标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="视频文件" prop="file">
          <el-upload ref="uploadElRef" :auto-upload="false" :limit="1"
            :on-change="handleFileChange" :on-remove="handleFileRemove" :on-exceed="handleExceed"
            accept="video/*" drag>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em></div>
            <template #tip><div class="el-upload__tip">MP4/AVI/MOV/WEBM，≤500MB</div></template>
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

const previewVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('')
const videoRef = ref<HTMLVideoElement>()

function getFileUrl(url:string|undefined):string {
  if(!url) return ''
  if(url.startsWith('http')) return url
  return '/api'+(url.startsWith('/')?url:'/'+url)
}
function showPreview(row:Material){
  const url=getFileUrl(row.fileUrl)
  if(!url){ ElMessage.warning('文件地址无效'); return }
  previewUrl.value=url; previewTitle.value=row.title; previewVisible.value=true
}
function closePreview(){
  if(videoRef.value){ videoRef.value.pause(); videoRef.value.src='' }
  previewUrl.value=''
}

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
    const res = await getMaterialList({ page:pagination.page, size:pagination.size, type:'video', projectId:projectId.value, keyword:keyword.value||undefined })
    tableData.value=res.records||[]; pagination.total=res.total||0
  }catch(e:any){ ElMessage.error(e.message||'加载失败') }
  finally { loading.value=false }
}

const uploading = ref(false)
const uploadVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadElRef = ref<UploadInstance>()
const selectedFile = ref<File|null>(null)
const uploadForm = reactive({ paragraphOrder:1, title:'' })
const uploadRules:FormRules = {
  title:[{ required:true, message:'请输入标题', trigger:'blur' }],
  paragraphOrder:[{ required:true, message:'请输入段落序号', trigger:'blur' }],
}

function showUploadDialog(){ resetUploadForm(); uploadVisible.value=true }
function resetUploadForm(){ uploadForm.paragraphOrder=1; uploadForm.title=''; selectedFile.value=null; uploadFormRef.value?.clearValidate(); uploadElRef.value?.clearFiles() }
function handleFileChange(f:any){ selectedFile.value=f.raw||null }
function handleFileRemove(){ selectedFile.value=null }
function handleExceed(){ ElMessage.warning('只能上传一个视频文件') }

async function handleUploadSubmit(){
  if(!uploadFormRef.value) return
  try {
    await uploadFormRef.value.validate()
    if(!selectedFile.value){ ElMessage.warning('请选择视频文件'); return }
    uploading.value=true
    await uploadMaterialFile(selectedFile.value,'video',projectId.value,uploadForm.title,{ paragraphOrder:uploadForm.paragraphOrder })
    ElMessage.success('上传成功'); uploadVisible.value=false; loadData()
  }catch(e:any){ if(e.message&&e.message!=='cancel') ElMessage.error(e.message) }
  finally { uploading.value=false }
}

function handleDownload(row:Material){
  const url=getFileUrl(row.fileUrl)
  if(!url){ ElMessage.warning('文件地址无效'); return }
  const a=document.createElement('a'); a.href=url; a.download=row.title||'视频'; a.target='_blank'; a.click()
}

async function handleDelete(row:Material){
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」？删除后进入回收站。`,'删除确认',{confirmButtonText:'确定',cancelButtonText:'取消',type:'warning'})
    await deleteMaterial(row.id); ElMessage.success('已删除'); loadData()
  }catch(e:any){ if(e!=='cancel'&&e!=='close'&&e.message) ElMessage.error(e.message) }
}

onBeforeUnmount(()=>{ if(videoRef.value){ videoRef.value.pause(); videoRef.value.src='' } })
onMounted(()=>{ loadData() })
</script>

<style scoped>
.page-container { padding:20px }
.toolbar { display:flex; gap:12px; margin-bottom:20px }
.card-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:16px }
.video-card { border:1px solid #ebeef5; border-radius:8px; overflow:hidden; transition:box-shadow .3s; background:#fff }
.video-card:hover { box-shadow:0 2px 12px rgba(0,0,0,.1) }
.card-preview { height:140px; background:#1a1a2e; display:flex; align-items:center; justify-content:center; cursor:pointer; position:relative; color:#fff }
.play-overlay { position:absolute; inset:0; background:rgba(0,0,0,.3); display:flex; align-items:center; justify-content:center; opacity:0; transition:opacity .3s }
.card-preview:hover .play-overlay { opacity:1 }
.card-info { padding:12px 16px; display:flex; flex-direction:column; gap:6px }
.card-header { display:flex; align-items:center; gap:8px }
.card-title { font-size:14px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; flex:1; min-width:0 }
.card-meta { display:flex; gap:10px; font-size:12px; color:#909399 }
.project-tag { background:#ecf5ff; color:#409eff; padding:0 6px; border-radius:3px; font-size:11px }
.card-actions { display:flex; gap:4px; margin-top:2px }
.pagination { margin-top:20px; display:flex; justify-content:flex-end }
.video-preview-box { background:#000; border-radius:8px; overflow:hidden }
.form-tip { color:#999; font-size:12px; margin-top:2px }
</style>
