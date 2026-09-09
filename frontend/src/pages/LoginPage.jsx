const providers = [
  { id: 'google',    label: 'Sign in with Google' },
  { id: 'microsoft', label: 'Sign in with Microsoft' },
  { id: 'apple',     label: 'Sign in with Apple' },
]

export default function LoginPage() {
  return (
    <div>
      <h1>Sign in</h1>
      {providers.map(({ id, label }) => (
        <a key={id} href={`/oauth2/authorization/${id}`}>{label}</a>
      ))}
    </div>
  )
}
