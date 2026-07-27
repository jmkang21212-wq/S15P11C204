import { RouterProvider } from 'react-router-dom'
import { router } from '@renderer/routes/router'

function App(): React.JSX.Element {
  return <RouterProvider router={router} />
}

export default App
