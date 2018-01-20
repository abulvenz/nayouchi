const propList = obj => {
  var properties = [];
  for (let p in obj) properties.push(p);
  return properties;
}

const contains = (arr, e) => arr.indexOf(e) >= 0;

const countProps = (arr) => {
  return arr.reduce((acc, curr) => {
    acc[curr] = acc[curr] + 1 || 1;
    return acc;
  }, {});
};

const pluck = (arr, prop) => arr.map(e => e[prop]);

const asKeyValueList = (obj) => {
  let props = propList(obj);
  return props.map(prop => {
    return {
      key: prop,
      value: obj[prop]
    };
  });
};

export default {
    asKeyValueList,
    pluck,
    countProps,
    propList,
    contains
}