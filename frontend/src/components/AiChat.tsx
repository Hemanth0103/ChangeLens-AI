
import { useEffect, useRef, useState, type FormEvent } from 'react'

import ReactMarkdown from 'react-markdown'

import './AiChat.css'

type DependencyMap = Record<string, string[]>
type ClassMethodsMap = Record<string, string[]>

type ChatMessage = {
  role: 'user' | 'assistant'
  content: string
}

type AiChatProps = {
  dependencies?: DependencyMap
  classMethods?: ClassMethodsMap
}

function AiChat({ dependencies, classMethods }: AiChatProps) {
  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null)

  const messagesEndRef = useRef<HTMLDivElement>(null)

  const hasProject =
    dependencies !== undefined &&
    Object.keys(dependencies).length > 0

  const classCount = dependencies
    ? Object.keys(dependencies).length
    : 0

  const methodCount = classMethods
    ? Object.values(classMethods).reduce(
        (total, methods) => total + methods.length,
        0
      )
    : 0

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
    })
  }, [messages, loading])

  useEffect(() => {
    setMessages([])
    setQuestion('')
    setError('')
    setCopiedIndex(null)
  }, [dependencies, classMethods])

  const copyAnswer = async (content: string, index: number) => {
    try {
      await navigator.clipboard.writeText(content)
      setCopiedIndex(index)
    } catch {
      setError('Unable to copy the answer.')
    }
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const trimmedQuestion = question.trim()

    if (!trimmedQuestion || loading || !hasProject) {
      return
    }

    setMessages((previous) => [
      ...previous,
      { role: 'user', content: trimmedQuestion },
    ])

    setQuestion('')
    setError('')
    setCopiedIndex(null)
    setLoading(true)

    try {
      const response = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          question: trimmedQuestion,
          dependencies,
          classMethods: classMethods ?? {},
        }),
      })

      if (!response.ok) {
        throw new Error(
          `AI request failed with status ${response.status}`
        )
      }

      const data: { answer?: string } = await response.json()

      if (!data.answer?.trim()) {
        throw new Error('AI returned an empty response')
      }

      const answer = data.answer

      setMessages((previous) => [
        ...previous,
        { role: 'assistant', content: answer },
      ])
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to contact the AI assistant'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="ai-chat">
      <div className="ai-chat-header">
        <div className="ai-chat-avatar">✦</div>

        <div>
          <h2>ChangeLens AI Assistant</h2>
          <p>
            <span className="ai-chat-status-dot" />
            Project-aware code intelligence
          </p>
        </div>
      </div>

      <div className="ai-chat-conversation" aria-live="polite">
        {messages.length === 0 && (
          <div className="ai-chat-welcome">
            <div className="ai-chat-welcome-icon">✦</div>

            <h3>How can I help with your code?</h3>

            <p>
              Explore Java dependencies, understand change impacts,
              and discover what to test before making changes.
            </p>

            {hasProject && (
              <button
                type="button"
                className="ai-chat-suggestion"
                onClick={() =>
                  setQuestion(
                    "Which classes have dependencies, and what changes could affect them?"
                  )
                }
              >
                Analyze my project's dependencies
              </button>
            )}
          </div>
        )}

        {messages.map((message, index) => (
          <div
            key={index}
            className={`ai-chat-message ai-chat-message-${message.role}`}
          >
            {message.role === 'assistant' && (
              <div className="ai-chat-message-avatar">✦</div>
            )}

            <div className="ai-chat-message-content">
              {message.role === 'assistant' ? (
                <div className="ai-chat-assistant-card">
                  <div className="ai-chat-assistant-label">
                    ChangeLens AI
                  </div>

                  <div className="ai-chat-markdown">
                    <ReactMarkdown>
                      {message.content}
                    </ReactMarkdown>
                  </div>

                  <div className="ai-chat-message-actions">
                    <button
                      type="button"
                      onClick={() =>
                        copyAnswer(message.content, index)
                      }
                      aria-label="Copy AI answer"
                    >
                      {copiedIndex === index
                        ? '✓ Copied'
                        : '⧉ Copy'}
                    </button>
                  </div>
                </div>
              ) : (
                <div className="ai-chat-user-bubble">
                  {message.content}
                </div>
              )}
            </div>
          </div>
        ))}

        {loading && (
          <div className="ai-chat-message ai-chat-message-assistant">
            <div className="ai-chat-message-avatar">✦</div>

            <div className="ai-chat-thinking">
              <span className="ai-chat-thinking-dot" />
              <span className="ai-chat-thinking-dot" />
              <span className="ai-chat-thinking-dot" />
              <span>Analyzing your project...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {error && (
        <div className="ai-chat-error" role="alert">
          {error}
        </div>
      )}

      <form className="ai-chat-composer" onSubmit={handleSubmit}>
        <textarea
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              event.currentTarget.form?.requestSubmit()
            }
          }}
          placeholder={
            hasProject
              ? 'Ask about your Java project...'
              : 'Upload a Java project to start chatting...'
          }
          disabled={!hasProject || loading}
          aria-label="Ask ChangeLens AI"
          rows={2}
        />

        <div className="ai-chat-composer-footer">
          <span className="ai-chat-context">
            <span className="ai-chat-status-dot" />

            {hasProject
              ? `${classCount} classes · ${methodCount} methods in context`
              : 'No project loaded'}
          </span>

          <button
            type="submit"
            className="ai-chat-send"
            disabled={
              !hasProject ||
              loading ||
              !question.trim()
            }
          >
            {loading ? 'Thinking...' : 'Send ↑'}
          </button>
        </div>
      </form>
    </div>
  )
}

export default AiChat
