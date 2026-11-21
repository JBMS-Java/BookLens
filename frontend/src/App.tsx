import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

const API_URL = 'http://localhost:8080/api/v1'

type Work = {
  id: string
  title: string
  author?: string
  sourceLanguage?: string
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
  const [comparison, setComparison] = useState<Array<{ editionId: string; language: string; analysis: Analysis }>>([])
  const [literary, setLiterary] = useState<LiteraryAnalysis | null>(null)
  const [message, setMessage] = useState('')
  const [activeView, setActiveView] = useState<'library' | 'import'>('library')

  useEffect(() => {
    void loadWorks()
  }, [])

  async function loadWorks() {
    const response = await fetch(`${API_URL}/works`)
    if (response.ok) setWorks(await response.json())
  }

  async function createWork(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
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
      setMessage('Não foi possível criar a obra.')
      return
    }
    event.currentTarget.reset()
    await loadWorks()
  }

  async function openWork(work: Work) {
    setSelectedWork(work)
    setSelectedEdition(null)
    setAnalysis(null)
    setPreview('')
    setTranslation('')
    setComparison([])
    setActiveView('library')
    const response = await fetch(`${API_URL}/works/${work.id}`)
    if (response.ok) {
      const data = await response.json()
      setEditions(data.editions ?? [])
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

    const response = await fetch(`${API_URL}/documents/import`, {
      method: 'POST',
      body,
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null)
      setMessage(error?.message ?? 'Falha na importação.')
      return
    }
    const edition = await response.json()
    setSelectedEdition(edition)
    await openWork(selectedWork)
    await loadEdition(edition.id)
  }

  async function loadEdition(editionId: string) {
    const [editionResponse, previewResponse] = await Promise.all([
      fetch(`${API_URL}/documents/${editionId}`),
      fetch(`${API_URL}/documents/${editionId}/preview`),
    ])
    if (editionResponse.ok) setSelectedEdition(await editionResponse.json())
    if (previewResponse.ok) {
      const data = await previewResponse.json()
      setPreview(data.preview ?? '')
    }

    const analysisResponse = await fetch(`${API_URL}/editions/${editionId}/analyses`)
    if (analysisResponse.ok) setAnalysis(await analysisResponse.json())

    const literaryResponse = await fetch(`${API_URL}/editions/${editionId}/literary`)
    if (literaryResponse.ok) setLiterary(await literaryResponse.json())
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
    const response = await fetch(`${API_URL}/translations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        editionId: selectedEdition.id,
        targetLanguage,
      }),
    })
    if (response.ok) {
      const data = await response.json()
      setTranslation(data.translatedText ?? '')
    }
  }

  function exportTranslation(format: 'txt' | 'docx') {
    if (!selectedEdition) return
    window.open(`${API_URL}/translations/${selectedEdition.id}/export?format=${format}`, '_blank')
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
    setSelectedWork(null)
    setEditions([])
    await loadWorks()
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand brand-button" onClick={() => setActiveView('library')} aria-label="BookLens">
          <span className="brand-mark">BL</span>
          <span>BookLens</span>
        </button>
        <nav className="topnav">
          <button className={activeView === 'library' ? 'active' : ''} onClick={() => setActiveView('library')}>
            Biblioteca
          </button>
          <button className={activeView === 'import' ? 'active' : ''} onClick={() => setActiveView('import')}>
            Importar
          </button>
        </nav>
        <span className="environment">Ambiente local</span>
      </header>

      {message && <div className="notice">{message}<button onClick={() => setMessage('')}>×</button></div>}

      <div className="layout">
        <aside className="sidebar">
          <div className="sidebar-heading">
            <span>Obras</span>
            <button onClick={() => setActiveView('import')}>+</button>
          </div>
          {works.map((work) => (
            <button
              key={work.id}
              className={`work-item ${selectedWork?.id === work.id ? 'selected' : ''}`}
              onClick={() => void openWork(work)}
            >
              <span className="work-cover">{work.title.slice(0, 1).toUpperCase()}</span>
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
            <div className="import-grid">
              <section className="panel">
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
              </section>

              <section className="panel">
                <p className="eyebrow">Importação</p>
                <h2>Adicionar edição</h2>
                <form className="form" onSubmit={importDocument}>
                  <label>Obra
                    <select value={selectedWork?.id ?? ''} onChange={(event) => {
                      const work = works.find((item) => item.id === event.target.value)
                      if (work) void openWork(work)
                    }}>
                      <option value="">Selecione uma obra</option>
                      {works.map((work) => <option key={work.id} value={work.id}>{work.title}</option>)}
                    </select>
                  </label>
                  <label>Título da edição<input name="title" /></label>
                  <label>Idioma
                    <select name="language" defaultValue="pt">
                      <option value="pt">Português</option>
                      <option value="en">English</option>
                      <option value="es">Español</option>
                    </select>
                  </label>
                  <label>Arquivo<input name="file" type="file" accept=".txt,.pdf,.docx,.epub" /></label>
                  <button type="submit" disabled={!selectedWork}>Importar arquivo</button>
                </form>
              </section>
            </div>
          )}

          {activeView === 'library' && (
            <>
              {!selectedWork && (
                <section className="welcome">
                  <p className="eyebrow">Biblioteca literária</p>
                  <h1>Leia, traduza e compare obras.</h1>
                  <p>Organize edições em português, inglês e espanhol e examine padrões de linguagem.</p>
                  <button onClick={() => setActiveView('import')}>Adicionar a primeira obra</button>
                </section>
              )}

              {selectedWork && (
                <section>
                  <header className="work-header">
                    <div>
                      <p className="eyebrow">{selectedWork.author || 'Autor não informado'}</p>
                      <h1>{selectedWork.title}</h1>
                    </div>
                    <div className="work-actions">
                      <button onClick={() => setActiveView('import')}>Adicionar edição</button>
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
                        <p className="eyebrow">Tradução</p>
                        <h2>Gerar versão</h2>
                        <div className="language-actions">
                          {['pt', 'en', 'es'].filter((language) => language !== selectedEdition.language).map((language) => (
                            <button key={language} onClick={() => void translateEdition(language)}>
                              Para {language === 'pt' ? 'Português' : language === 'en' ? 'English' : 'Español'}
                            </button>
                          ))}
                        </div>
                        {translation && (
                          <>
                            <pre className="translation-text">{translation}</pre>
                            <div className="export-actions">
                              <button onClick={() => exportTranslation('txt')}>Exportar TXT</button>
                              <button onClick={() => exportTranslation('docx')}>Exportar DOCX</button>
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
                        <p className="eyebrow">Gerenciamento</p>
                        <h2>Edição atual</h2>
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
