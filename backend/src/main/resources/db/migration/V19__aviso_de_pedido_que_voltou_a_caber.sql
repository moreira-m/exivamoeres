-- O P18 (V16) e o P21 (V18) avisam quando o pedido pendente PASSA A NÃO CABER — level
-- mínimo acima do personagem, ou composição sem vaga para a vocação. O caminho inverso
-- era silencioso: o dono baixa o requisito ou devolve a vaga, o pedido volta a ser
-- aprovável, e quem pediu não fica sabendo. A pessoa desistiu mentalmente do pedido (às
-- vezes cancelou) sem saber que agora dava.
--
-- Um tipo só para as duas causas, ao contrário da má notícia: aqui a ação de quem
-- recebe é a mesma nos dois casos — nenhuma, é só esperar a aprovação. O que muda entre
-- "subiu o level" e "sumiu a vaga" é o que a pessoa PRECISA FAZER, e por isso a má
-- notícia tem dois tipos; a boa não precisa.
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_APPROVED', 'JOIN_REQUEST_REJECTED',
        'KICKED_FROM_TEAM', 'TEAM_DELETED', 'MEMBER_LEFT',
        'TEAM_SCHEDULE_CHANGED', 'TEAM_MINIMUM_LEVEL_CHANGED',
        'JOIN_REQUEST_AT_RISK', 'JOIN_REQUEST_COMPOSITION_MISMATCH',
        'JOIN_REQUEST_FITS_AGAIN'));
