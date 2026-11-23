import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import './App.css'

const API_URL = 'http://localhost:8080/api/v1'

type Work = {
  id: string
  title: string
  author?: string
  sourceLanguage?: string
  coverVersion?: string | null
}

function coverUrl(work: Work) {
  return `${API_URL}/works/${work.id}/cover?v=${encodeURIComponent(work.coverVersion ?? '')}`
}

type Edition = {
  id: string
  workId: string
  title: string
  language: string
  originalFilename: string
  format: string
  status: string
  extractedText: string
}

type Analysis = {
  characterCount: number
  wordCount: number
  uniqueWordCount: number
  lexicalRichness: number
  sentenceCount: number
  paragraphCount: number
  averageWordsPerSentence: number
  averageWordLength: number
  estimatedReadability: number
  topWords: Array<{ word: string; count: number }>
}

type LiteraryAnalysis = {
  tone: string
  positiveSignals: number
  negativeSignals: number
  narrativeDensity: string
  structure: string
  disclaimer: string
}

function App() {
  const [works, setWorks] = useState<Work[]>([])
  const [selectedWork, setSelectedWork] = useState<Work | null>(null)
  const [editions, setEditions] = useState<Edition[]>([])
  const [selectedEdition, setSelectedEdition] = useState<Edition | null>(null)
  const [analysis, setAnalysis] = useState<Analysis | null>(null)
  const [preview, setPreview] = useState('')
  const [translation, setTranslation] = useState('')
  const [translationLanguage, setTranslationLanguage] = useState('')
  const selectedEditionId = useRef<string | null>(null)
  const editionLoadVersion = useRef(0)
  const translationVersion = useRef(0)
  const [isTranslating, setIsTranslating] = useState(false)
  const [isImporting, setIsImporting] = useState(false)
  const [isUploadingCover, setIsUploadingCover] = useState(false)
  const [comparison, setComparison] = useState<Array<{ editionId: string; language: string; analysis: Analysis }>>([])
  const [literary, setLiterary] = useState<LiteraryAnalysis | null>(null)
  const [message, setMessage] = useState('')
  const [activeView, setActiveView] = useState<'library' | 'import'>('library')
  const [addingEdition, setAddingEdition] = useState(false)

  useEffect(() => {
    void loadWorks()
  }, [])

  async function loadWorks(): Promise<Work[]> {
    const response = await fetch(`${API_URL}/works`)
    if (!response.ok) {
      throw new Error('Não foi possível carregar a biblioteca.')
    }
    const loadedWorks: Work[] = await response.json()
    setWorks(loadedWorks)
    return loadedWorks
  }

  async function createWork(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    try {
      const response = await fetch(`${API_URL}/works`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: form.get('title'),
          author: form.get('author'),
          language: form.get('language'),
        }),
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null)
        setMessage(error?.message ?? 'Não foi possível criar a obra.')
        return
      }

      const createdWork: Work = await response.json()
      formElement.reset()
      await loadWorks()
      await openWork(createdWork)
      setAddingEdition(true)
      setActiveView('import')
      setMessage('Obra criada. Agora selecione o PDF na seção “Adicionar edição”.')
    } catch {
      setMessage('Não foi possível conectar ao servidor. Verifique se o BookLens está em execução e tente novamente.')
    }
  }

  async function openWork(work: Work, keepImport = false) {
    selectedEditionId.current = null
    editionLoadVersion.current++
    translationVersion.current++
    setSelectedWork(work)
    setSelectedEdition(null)
    setAnalysis(null)
    setPreview('')
    setTranslation('')
    setTranslationLanguage('')
    setComparison([])
    setActiveView(keepImport ? 'import' : 'library')
    const response = await fetch(`${API_URL}/works/${work.id}`)
    if (response.ok) {
      const data = await response.json()
      setEditions(data.editions ?? [])
    }
  }

  async function uploadCover(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget
    const file = input.files?.[0]
    input.value = ''
    if (!selectedWork || !file) return
    if (file.size === 0 || file.size > 5 * 1024 * 1024) {
      setMessage('A capa deve ter até 5 MB.')
      return
    }

    const workId = selectedWork.id
    const body = new FormData()
    body.set('file', file)
    setIsUploadingCover(true)
    try {
      const response = await fetch(`${API_URL}/works/${workId}/cover`, { method: 'PUT', body })
      if (!response.ok) {
        const error = await response.json().catch(() => null)
        setMessage(error?.detail ?? error?.message ?? 'Não foi possível salvar a capa.')
        return
      }
      const updatedWork: Work = await response.json()
      setWorks((current) => current.map((work) => work.id === workId ? updatedWork : work))
      setSelectedWork((current) => current?.id === workId ? updatedWork : current)
      setMessage('Capa salva com sucesso.')
    } catch {
      setMessage('Não foi possível conectar ao servidor para salvar a capa.')
    } finally {
      setIsUploadingCover(false)
    }
  }

  async function importDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedWork) {
      setMessage('Selecione uma obra primeiro.')
      return
    }
    const form = new FormData(event.currentTarget)
    const file = form.get('file') as File
    if (!file || file.size === 0) {
      setMessage('Selecione um arquivo.')
      return
    }

    const body = new FormData()
    body.set('file', file)
    body.set('workId', selectedWork.id)
    body.set('title', String(form.get('title') || file.name))
    body.set('language', String(form.get('language')))

    setIsImporting(true)
    try {
      const response = await fetch(`${API_URL}/documents/import`, {
        method: 'POST',
        body,
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null)
        setMessage(error?.detail ?? error?.message ?? 'Falha na importação.')
        return
      }
      const edition = await response.json()
      await openWork(selectedWork)
      await loadEdition(edition.id)
      setMessage(
        edition.status === 'OCR_REQUIRED'
          ? 'O PDF foi enviado, mas não contém texto selecionável. É necessário OCR antes de traduzir.'
          : `Arquivo lido por completo: ${edition.extractedText.length.toLocaleString()} caracteres.`,
      )
    } catch {
      setMessage('Não foi possível enviar o arquivo. Verifique a conexão com o BookLens e tente novamente.')
    } finally {
      setIsImporting(false)
    }
  }

  async function loadEdition(editionId: string) {
    selectedEditionId.current = editionId
    const loadVersion = ++editionLoadVersion.current
    setSelectedEdition(null)
    setPreview('')
    setAnalysis(null)
    setLiterary(null)
    setTranslation('')
    setTranslationLanguage('')
    const [editionResponse, previewResponse] = await Promise.all([
      fetch(`${API_URL}/documents/${editionId}`),
      fetch(`${API_URL}/documents/${editionId}/preview`),
    ])
    if (loadVersion !== editionLoadVersion.current) return
    if (!editionResponse.ok) {
      setMessage('Não foi possível carregar a edição.')
      return
    }
    const edition = await editionResponse.json()
    if (loadVersion !== editionLoadVersion.current) return
    setSelectedEdition(edition)
    if (previewResponse.ok) {
      const data = await previewResponse.json()
      if (loadVersion !== editionLoadVersion.current) return
      setPreview(data.preview ?? '')
    }

    const analysisResponse = await fetch(`${API_URL}/editions/${editionId}/analyses`)
    if (loadVersion !== editionLoadVersion.current) return
    if (analysisResponse.ok) {
      const data = await analysisResponse.json()
      if (loadVersion !== editionLoadVersion.current) return
      setAnalysis(data)
    }

    const literaryResponse = await fetch(`${API_URL}/editions/${editionId}/literary`)
    if (loadVersion !== editionLoadVersion.current) return
    if (literaryResponse.ok) {
      const data = await literaryResponse.json()
      if (loadVersion !== editionLoadVersion.current) return
      setLiterary(data)
    }

    const currentTranslationVersion = translationVersion.current
    const translationResponse = await fetch(`${API_URL}/translations/${editionId}`)
    if (loadVersion !== editionLoadVersion.current || currentTranslationVersion !== translationVersion.current) return
    if (translationResponse.ok) {
      const savedTranslation = await translationResponse.json()
      if (loadVersion !== editionLoadVersion.current || currentTranslationVersion !== translationVersion.current) return
      setTranslation(savedTranslation.translatedText ?? '')
      setTranslationLanguage(savedTranslation.targetLanguage ?? '')
    } else if (translationResponse.status !== 404) {
      setMessage('Não foi possível carregar a tradução salva.')
    }
  }

  async function runAnalysis() {
    if (!selectedEdition) return
    const response = await fetch(`${API_URL}/editions/${selectedEdition.id}/analyses`, {
      method: 'POST',
    })
    if (response.ok) setAnalysis(await response.json())
    const literaryResponse = await fetch(`${API_URL}/editions/${selectedEdition.id}/literary`)
    if (literaryResponse.ok) setLiterary(await literaryResponse.json())
  }

  async function runComparison() {
    if (!selectedWork) return
    const response = await fetch(`${API_URL}/works/${selectedWork.id}/comparison`)
    if (response.ok) {
      const data = await response.json()
      setComparison(data.editions ?? [])
    }
  }

  async function translateEdition(targetLanguage: string) {
    if (!selectedEdition) return
    const editionId = selectedEdition.id
    setIsTranslating(true)
    setMessage('')
    try {
      const response = await fetch(`${API_URL}/translations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          editionId,
          targetLanguage,
        }),
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null)
        setMessage(error?.detail ?? error?.message
          ?? 'Não foi possível traduzir o livro. Verifique se o LibreTranslate está em execução.')
        return
      }
      const data = await response.json()
      translationVersion.current++
      if (selectedEditionId.current === editionId) {
        setTranslation(data.translatedText ?? '')
        setTranslationLanguage(data.targetLanguage ?? targetLanguage)
        setMessage(`Tradução completa salva (${data.translatedCharacters ?? data.translatedText?.length ?? 0} caracteres).`)
      }
    } catch {
      setMessage('Não foi possível conectar ao servidor de tradução.')
    } finally {
      setIsTranslating(false)
    }
  }

  async function exportTranslation(format: 'txt' | 'docx' | 'pdf') {
    if (!selectedEdition) return
    try {
      const response = await fetch(
        `${API_URL}/translations/${selectedEdition.id}/export?format=${encodeURIComponent(format)}`,
      )
      if (!response.ok) {
        const error = await response.json().catch(() => null)
        setMessage(error?.detail ?? error?.message ?? 'Gere a tradução antes de baixar o arquivo.')
        return
      }
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${selectedEdition.title.replace(/[^\p{L}\p{N}._-]+/gu, '_')}-${translationLanguage || 'traduzido'}.${format}`
      document.body.append(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch {
      setMessage('Não foi possível baixar o arquivo. Verifique se o servidor está disponível.')
    }
  }

  async function deleteEdition() {
    if (!selectedEdition || !selectedWork) return
    if (!window.confirm('Excluir esta edição? Esta ação não pode ser desfeita.')) return
    const response = await fetch(`${API_URL}/documents/${selectedEdition.id}`, { method: 'DELETE' })
    if (!response.ok) return
    setSelectedEdition(null)
    await openWork(selectedWork)
  }

  async function deleteWork() {
    if (!selectedWork) return
    if (!window.confirm('Excluir esta obra e todas as suas edições?')) return
    const response = await fetch(`${API_URL}/works/${selectedWork.id}`, { method: 'DELETE' })
    if (!response.ok) return
    selectedEditionId.current = null
    editionLoadVersion.current++
    translationVersion.current++
    setSelectedWork(null)
    setEditions([])
    await loadWorks()
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand brand-button" onClick={() => setActiveView('library')} aria-label="BookLens">
          <span className="brand-mark">BL</span>
          <span className="brand-name">BookLens<small>arquivo literário</small></span>
        </button>
        <nav className="topnav">
          <button className={activeView === 'library' ? 'active' : ''} onClick={() => setActiveView('library')}>
            Biblioteca
          </button>
          <button className={activeView === 'import' ? 'active' : ''} onClick={() => {
            setAddingEdition(false)
            setActiveView('import')
          }}>
            Importar
          </button>
        </nav>
        <span className="environment">Ambiente local</span>
      </header>

      {message && <div className="notice">{message}<button onClick={() => setMessage('')}>×</button></div>}

      <div className="layout">
        <aside className="sidebar">
          <div className="sidebar-heading">
            <span>Meu acervo <small>{works.length.toString().padStart(2, '0')}</small></span>
            <button aria-label="Adicionar obra" onClick={() => {
              setAddingEdition(false)
              setActiveView('import')
            }}>+</button>
          </div>
          {works.map((work) => (
            <button
              key={work.id}
              className={`work-item ${selectedWork?.id === work.id ? 'selected' : ''}`}
              onClick={() => void openWork(work)}
            >
              <span className="work-cover">
                {work.coverVersion
                  ? <img src={coverUrl(work)} alt="" />
                  : work.title.slice(0, 1).toUpperCase()}
              </span>
              <span>
                <strong>{work.title}</strong>
                <small>{work.author || 'Autor não informado'}</small>
              </span>
            </button>
          ))}
          {works.length === 0 && <p className="empty-note">Nenhuma obra criada.</p>}
        </aside>

        <section className="content">
          {activeView === 'import' && (
            <div className={`import-grid ${addingEdition ? 'edition-import' : ''}`}>
              {!addingEdition && <section className="panel">
                <p className="eyebrow">Nova obra</p>
                <h2>Criar registro</h2>
                <form className="form" onSubmit={createWork}>
                  <label>Título<input name="title" required /></label>
                  <label>Autor<input name="author" /></label>
                  <label>Idioma original
                    <select name="language" defaultValue="pt">
                      <option value="pt">Português</option>
                      <option value="en">English</option>
                      <option value="es">Español</option>
                    </select>
                  </label>
                  <button type="submit">Criar obra</button>
                </form>
              </section>}

              <section className="panel">
                <p className="eyebrow">Importação</p>
                <h2>Adicionar edição</h2>
                <form className="form" onSubmit={importDocument}>
                  {addingEdition && selectedWork ? (
                    <div className="selected-import-work">
                      <span>Obra selecionada</span>
                      <strong>{selectedWork.title}</strong>
                    </div>
                  ) : <label>Obra
                    <select value={selectedWork?.id ?? ''} onChange={(event) => {
                      const work = works.find((item) => item.id === event.target.value)
                      if (work) void openWork(work, true)
                    }}>
                      <option value="">Selecione uma obra</option>
                      {works.map((work) => <option key={work.id} value={work.id}>{work.title}</option>)}
                    </select>
                  </label>}
                  <label>Título da edição<input name="title" /></label>
                  <label>Idioma
                    <select name="language" defaultValue="pt">
                      <option value="pt">Português</option>
                      <option value="en">English</option>
                      <option value="es">Español</option>
                    </select>
                  </label>
                  <label>Arquivo<input name="file" type="file" accept=".txt,.pdf,.docx,.epub" /></label>
                  <button type="submit" disabled={!selectedWork || isImporting}>
                    {isImporting ? 'Lendo o arquivo inteiro…' : 'Importar arquivo'}
                  </button>
                </form>
              </section>
            </div>
          )}

          {activeView === 'library' && (
            <>
              {!selectedWork && (
                <section className="welcome">
                  <div className="welcome-ornament" aria-hidden="true"><span>✦</span></div>
                  <p className="eyebrow">Seu arquivo literário</p>
                  <h1>Leia, traduza e compare obras.</h1>
                  <p>Um lugar para guardar histórias, atravessar idiomas e descobrir novas leituras.</p>
                  <button onClick={() => {
                    setAddingEdition(false)
                    setActiveView('import')
                  }}>Adicionar a primeira obra</button>
                  <div className="welcome-details" aria-hidden="true">
                    <span>01 <b>Importe seus livros</b></span>
                    <span>02 <b>Explore as palavras</b></span>
                    <span>03 <b>Traduza histórias</b></span>
                  </div>
                </section>
              )}

              {selectedWork && (
                <section>
                  <header className="work-header">
                    <div className="work-identity">
                      <span className="work-cover work-cover-large">
                        {selectedWork.coverVersion
                          ? <img src={coverUrl(selectedWork)} alt="" />
                          : selectedWork.title.slice(0, 1).toUpperCase()}
                      </span>
                      <div>
                        <p className="eyebrow">{selectedWork.author || 'Autor não informado'}</p>
                        <h1>{selectedWork.title}</h1>
                      </div>
                    </div>
                    <div className="work-actions">
                      <label className={`cover-upload ${isUploadingCover ? 'disabled' : ''}`}>
                        {isUploadingCover ? 'Salvando capa…' : selectedWork.coverVersion ? 'Trocar capa' : 'Adicionar capa'}
                        <input type="file" accept="image/jpeg,image/png" disabled={isUploadingCover} onChange={(event) => void uploadCover(event)} />
                      </label>
                      <button onClick={() => {
                        setAddingEdition(true)
                        setActiveView('import')
                      }}>Adicionar edição</button>
                      <button className="danger-button" onClick={() => void deleteWork()}>Excluir obra</button>
                    </div>
                  </header>

                  <div className="edition-tabs">
                    {editions.map((edition) => (
                      <button
                        key={edition.id}
                        className={selectedEdition?.id === edition.id ? 'active' : ''}
                        onClick={() => void loadEdition(edition.id)}
                      >
                        {edition.language.toUpperCase()} · {edition.title}
                        <small>{edition.format.toUpperCase()} · {edition.status}</small>
                      </button>
                    ))}
                    {editions.length === 0 && <p className="empty-note">Esta obra ainda não possui edições.</p>}
                  </div>

                  {selectedEdition && (
                    <div className="edition-grid">
                      <section className="panel preview-panel">
                        <div className="panel-heading">
                          <div>
                            <p className="eyebrow">Conteúdo extraído</p>
                            <h2>Prévia</h2>
                          </div>
                          <button onClick={() => void runAnalysis()}>Executar análise</button>
                        </div>
                        {selectedEdition.status === 'OCR_REQUIRED'
                          ? <p className="ocr-note">Este PDF não contém texto selecionável. A etapa de OCR ainda não está ativa.</p>
                          : <pre>{preview || 'Documento sem texto extraído.'}</pre>}
                      </section>

                      <section className="panel metrics-panel">
                        <p className="eyebrow">Estatísticas</p>
                        <h2>Análise textual</h2>
                        {analysis ? (
                          <>
                            <div className="metric-grid">
                              <div><strong>{analysis.wordCount}</strong><span>palavras</span></div>
                              <div><strong>{analysis.uniqueWordCount}</strong><span>únicas</span></div>
                              <div><strong>{analysis.sentenceCount}</strong><span>frases</span></div>
                              <div><strong>{analysis.paragraphCount}</strong><span>parágrafos</span></div>
                            </div>
                            <dl>
                              <div><dt>Riqueza lexical</dt><dd>{(analysis.lexicalRichness * 100).toFixed(1)}%</dd></div>
                              <div><dt>Média por frase</dt><dd>{analysis.averageWordsPerSentence}</dd></div>
                              <div><dt>Tamanho médio</dt><dd>{analysis.averageWordLength} caracteres</dd></div>
                              <div><dt>Legibilidade</dt><dd>{analysis.estimatedReadability}</dd></div>
                            </dl>
                            <h3>Palavras frequentes</h3>
                            <div className="word-list">
                              {analysis.topWords.map((item) => (
                                <span key={item.word}>{item.word}<b>{item.count}</b></span>
                              ))}
                            </div>
                          </>
                        ) : <p className="empty-note">Execute a análise para visualizar as métricas.</p>}
                      </section>

                      <section className="panel literary-panel">
                        <p className="eyebrow">Análise literária</p>
                        <h2>Indícios do texto</h2>
                        {literary ? (
                          <>
                            <div className="literary-highlight">
                              <span>Tom predominante</span>
                              <strong>{literary.tone}</strong>
                            </div>
                            <dl>
                              <div><dt>Densidade narrativa</dt><dd>{literary.narrativeDensity}</dd></div>
                              <div><dt>Estrutura</dt><dd>{literary.structure}</dd></div>
                              <div><dt>Sinais positivos</dt><dd>{literary.positiveSignals}</dd></div>
                              <div><dt>Sinais sombrios</dt><dd>{literary.negativeSignals}</dd></div>
                            </dl>
                            <p className="disclaimer">{literary.disclaimer}</p>
                          </>
                        ) : <p className="empty-note">Execute a análise para gerar os indícios literários.</p>}
                      </section>

                      <section className="panel translation-panel">
                        <p className="eyebrow">Tradução integral</p>
                        <h2>Traduzir o livro inteiro</h2>
                        <p className="translation-note">
                          O texto extraído completo será enviado ao LibreTranslate local. Livros longos podem levar bastante tempo.
                        </p>
                        <div className="language-actions">
                          {selectedEdition.status === 'OCR_REQUIRED' && (
                            <p className="ocr-note">
                              Este PDF parece digitalizado. A tradução só ficará disponível depois de reconhecer o texto com OCR.
                            </p>
                          )}
                          {['en', 'es'].filter((language) => language !== selectedEdition.language).map((language) => (
                            <button
                              key={language}
                              disabled={isTranslating || isImporting || selectedEdition.status === 'OCR_REQUIRED'}
                              onClick={() => void translateEdition(language)}
                            >
                              {isTranslating
                                ? 'Traduzindo livro inteiro…'
                                : `Traduzir para ${language === 'pt' ? 'Português' : language === 'en' ? 'Inglês' : 'Espanhol'}`}
                            </button>
                          ))}
                        </div>
                        {translation && (
                          <>
                            <p className="translation-saved">
                              Tradução integral salva · {translation.length.toLocaleString()} caracteres
                              {translationLanguage && ` · ${translationLanguage.toUpperCase()}`}
                            </p>
                            <pre className="translation-text">
                              {translation.slice(0, 5000)}
                              {translation.length > 5000 ? '\n\n… Prévia. O arquivo para download contém a tradução integral.' : ''}
                            </pre>
                            <div className="export-actions">
                              <button disabled={isTranslating || isImporting} onClick={() => void exportTranslation('docx')}>Baixar Word (.docx)</button>
                              <button disabled={isTranslating || isImporting} onClick={() => void exportTranslation('pdf')}>Baixar PDF</button>
                              <button disabled={isTranslating || isImporting} onClick={() => void exportTranslation('txt')}>Baixar TXT</button>
                            </div>
                          </>
                        )}
                      </section>

                      <section className="panel comparison-panel">
                        <div className="panel-heading">
                          <div>
                            <p className="eyebrow">Comparação</p>
                            <h2>Edições da obra</h2>
                          </div>
                          <button onClick={() => void runComparison()}>Comparar</button>
                        </div>
                        {comparison.length > 0 ? (
                          <div className="comparison-grid">
                            {comparison.map((item) => (
                              <div key={item.editionId}>
                                <span>{item.language.toUpperCase()}</span>
                                <strong>{item.analysis.wordCount}</strong>
                                <small>palavras</small>
                                <strong>{item.analysis.uniqueWordCount}</strong>
                                <small>palavras únicas</small>
                              </div>
                            ))}
                          </div>
                        ) : <p className="empty-note">Analise pelo menos uma edição antes de comparar.</p>}
                      </section>

                      <section className="panel edition-actions-panel">
                        <div>
                          <p className="eyebrow">Gerenciamento</p>
                          <h2>Edição atual</h2>
                        </div>
                        <button className="danger-button" onClick={() => void deleteEdition()}>
                          Excluir edição
                        </button>
                      </section>
                    </div>
                  )}
                </section>
              )}
            </>
          )}
        </section>
      </div>
    </main>
  )
}

export default App
