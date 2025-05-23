import { t } from "ttag";
import _ from "underscore";

import ModalContent from "metabase/components/ModalContent";
import Text from "metabase/components/type/Text";
import Button from "metabase/core/components/Button";
import CS from "metabase/css/core/index.css";
import Users from "metabase/entities/users";
import { connect } from "metabase/lib/redux";
import type { User } from "metabase-types/api";

interface UserActivationModalInnerProps {
  user: User & {
    reactivate: () => void | Promise<void>;
    deactivate: () => void | Promise<void>;
  };
  onClose: () => void;
}

// NOTE: we have to load the list of users because /api/user/:id doesn't return deactivated users
// but that's ok because it's probably already loaded through the people PeopleListingApp
const UserActivationModalInner = ({
  user,
  onClose,
}: UserActivationModalInnerProps) => {
  if (!user) {
    return null;
  }

  if (user.is_active) {
    return (
      <ModalContent
        title={t`Deactivate ${user.common_name}?`}
        onClose={onClose}
      >
        <Text>{t`${user.common_name} won't be able to log in anymore.`}</Text>
        <Button
          className={CS.mlAuto}
          danger
          onClick={() => user.deactivate() && onClose()}
        >
          {t`Deactivate`}
        </Button>
      </ModalContent>
    );
  } else {
    return (
      <ModalContent
        title={t`Reactivate ${user.common_name}?`}
        onClose={onClose}
      >
        <Text>
          {t`They'll be able to log in again, and they'll be placed back into the groups they were in before their account was deactivated.`}
        </Text>
        <Button
          className={CS.mlAuto}
          danger
          onClick={() => user.reactivate() && onClose()}
        >
          {t`Reactivate`}
        </Button>
      </ModalContent>
    );
  }
};

export const UserActivationModal = _.compose(
  Users.loadList({
    query: { include_deactivated: true },
    wrapped: true,
  }),
  connect(
    (
      _state,
      {
        users,
        params: { userId },
      }: {
        users: User[];
        params: { userId: string };
      },
    ) => ({ user: _.findWhere(users, { id: parseInt(userId) }) }),
  ),
)(UserActivationModalInner);
