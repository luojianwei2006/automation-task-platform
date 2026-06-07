<template>
  <div class="comment-container">
    <h2 class="page-title">评论词管理</h2>
    <p class="page-sub">管理自动化评论词库，支持多分类</p>

    <el-row :gutter="20">
      <!-- 左侧：分类列表 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>分类</span>
              <el-button size="small" type="primary" @click="showAddCat = true">+ 新增</el-button>
            </div>
          </template>
          <div
            v-for="cat in categories" :key="cat.id"
            :class="['cat-item', { active: selectedCatId === cat.id }]"
            @click="selectCategory(cat)"
          >
            <span>{{ cat.name }} {{ cat.isDefault ? '(默认)' : '' }}</span>
            <span v-if="cat.isDefault !== 1" style="display:flex;gap:4px">
              <el-button link size="small" @click.stop="editCat(cat)">编辑</el-button>
              <el-button link size="small" type="danger" @click.stop="delCat(cat)">删除</el-button>
            </span>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：评论词列表 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between">
              <span>评论词 ({{ words.length }})</span>
              <el-button size="small" type="primary" @click="showAddWord = true" :disabled="!selectedCatId">+ 添加</el-button>
            </div>
          </template>
          <el-tag
            v-for="w in words" :key="w.id"
            closable
            style="margin:4px"
            @close="delWord(w)"
          >{{ w.content }}</el-tag>
          <el-empty v-if="words.length === 0" description="暂无评论词" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑分类 -->
    <el-dialog v-model="showAddCat" :title="editingCat ? '编辑分类' : '新增分类'" width="350px">
      <el-input v-model="catName" placeholder="分类名称" maxlength="20" />
      <template #footer>
        <el-button @click="showAddCat = false">取消</el-button>
        <el-button type="primary" @click="saveCat">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加评论词 -->
    <el-dialog v-model="showAddWord" title="添加评论词" width="400px">
      <el-input v-model="wordContent" placeholder="输入评论内容" maxlength="200" type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="showAddWord = false">取消</el-button>
        <el-button type="primary" @click="saveWord">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategories, addCategory, updateCategory, deleteCategory, getWords, addWord, deleteWord } from '@/api/comment'
import type { CommentCategory, CommentWord } from '@/api/comment'

const categories = ref<CommentCategory[]>([])
const words = ref<CommentWord[]>([])
const selectedCatId = ref<number | null>(null)

const showAddCat = ref(false)
const showAddWord = ref(false)
const catName = ref('')
const wordContent = ref('')
const editingCat = ref<CommentCategory | null>(null)

onMounted(loadCategories)

async function loadCategories() {
  const res: any = await getCategories()
  categories.value = res ?? []
  if (categories.value.length > 0) selectCategory(categories.value[0])
}

function selectCategory(cat: CommentCategory) {
  selectedCatId.value = cat.id
  loadWords(cat.id)
}

async function loadWords(catId: number) {
  const res: any = await getWords(catId)
  words.value = res ?? []
}

function editCat(cat: CommentCategory) {
  editingCat.value = cat; catName.value = cat.name; showAddCat.value = true
}

async function saveCat() {
  if (!catName.value.trim()) { ElMessage.warning('请输入分类名'); return }
  if (editingCat.value) {
    await updateCategory(editingCat.value.id, { name: catName.value.trim() })
  } else {
    await addCategory({ name: catName.value.trim() })
  }
  showAddCat.value = false
  editingCat.value = null
  catName.value = ''
  loadCategories()
}

async function delCat(cat: CommentCategory) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${cat.name}」及其所有评论词？`, '确认', { type: 'warning' })
    await deleteCategory(cat.id)
    selectedCatId.value = null
    words.value = []
    loadCategories()
  } catch {}
}

async function saveWord() {
  if (!wordContent.value.trim() || !selectedCatId.value) return
  await addWord({ categoryId: selectedCatId.value, content: wordContent.value.trim() })
  wordContent.value = ''
  showAddWord.value = false
  loadWords(selectedCatId.value)
}

async function delWord(w: CommentWord) {
  await deleteWord(w.id)
  if (selectedCatId.value) loadWords(selectedCatId.value)
}
</script>

<style scoped>
.page-title { margin:0;font-size:18px;font-weight:600 }
.page-sub { color:#909399;font-size:13px;margin:4px 0 16px }
.cat-item { padding:10px 12px;cursor:pointer;border-radius:6px;display:flex;justify-content:space-between;align-items:center;margin-bottom:4px }
.cat-item:hover { background:#f5f7fa }
.cat-item.active { background:#ecf5ff;color:#409eff;font-weight:500 }
</style>
