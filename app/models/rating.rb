class Rating < ActiveRecord::Base
  attr_accessible :anime_id, :rating, :user_id
end
