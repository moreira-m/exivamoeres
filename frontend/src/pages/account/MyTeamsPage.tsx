import { Link } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { TeamCard } from '../../components/TeamCard'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { useMyLists } from '../../hooks/useLists'

export function MyTeamsPage() {
  const myLists = useMyLists()

  return (
    <Layout>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">Meus times</h1>
        <Link to="/account/teams/new">
          <Button variant="accent">Criar time</Button>
        </Link>
      </div>

      {myLists.isLoading ? (
        <Spinner />
      ) : myLists.data && myLists.data.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {myLists.data.map((team) => (
            <TeamCard key={team.id} team={team} />
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center font-bold">
          Você ainda não participa de nenhum time. Crie um ou encontre um na página inicial.
        </Card>
      )}
    </Layout>
  )
}
