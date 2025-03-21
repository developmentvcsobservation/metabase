import type { ColumnItem } from "metabase/querying/filters/types";
import * as Lib from "metabase-lib";

function isCreationDateOrTimestamp(column: Lib.ColumnMetadata) {
  return Lib.isCreationDate(column) || Lib.isCreationTimestamp(column);
}

function isCategoryAndNotNameOrAddress(column: Lib.ColumnMetadata) {
  return (
    Lib.isCategory(column) &&
    !Lib.isEntityName(column) &&
    !Lib.isTitle(column) &&
    !Lib.isAddress(column)
  );
}

function isNumberAndNotKeyOrCoordinate(column: Lib.ColumnMetadata) {
  return (
    Lib.isNumeric(column) &&
    !Lib.isPrimaryKey(column) &&
    !Lib.isForeignKey(column) &&
    !Lib.isCoordinate(column)
  );
}

function isShortText(column: Lib.ColumnMetadata) {
  return Lib.isStringOrStringLike(column) && !isLongText(column);
}

function isLongText(column: Lib.ColumnMetadata) {
  return Lib.isComment(column) || Lib.isDescription(column);
}

const PRIORITIES = [
  isCreationDateOrTimestamp,
  Lib.isCreationTime,
  Lib.isTemporal,
  Lib.isBoolean,
  isCategoryAndNotNameOrAddress,
  Lib.isCurrency,
  Lib.isCity,
  Lib.isState,
  Lib.isZipCode,
  Lib.isCountry,
  isNumberAndNotKeyOrCoordinate,
  isShortText,
  Lib.isPrimaryKey,
  Lib.isLatitude,
  Lib.isLongitude,
  isLongText,
  Lib.isForeignKey,
  () => true,
];

export function sortColumns(columnItems: ColumnItem[]): ColumnItem[] {
  return columnItems
    .map(columnItem => ({
      priority: PRIORITIES.findIndex(predicate => predicate(columnItem.column)),
      columnItem,
    }))
    .sort((a, b) => a.priority - b.priority)
    .map(({ columnItem }) => columnItem);
}
